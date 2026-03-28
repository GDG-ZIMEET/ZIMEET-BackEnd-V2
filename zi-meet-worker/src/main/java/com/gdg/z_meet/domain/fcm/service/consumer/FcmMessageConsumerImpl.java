package com.gdg.z_meet.domain.fcm.service.consumer;

import com.gdg.z_meet.domain.fcm.dto.FcmMessageRequest;
import com.gdg.z_meet.domain.fcm.entity.FcmToken;
import com.gdg.z_meet.domain.fcm.repository.FcmTokenRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.google.firebase.messaging.BatchResponse;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.MulticastMessage;
import com.google.firebase.messaging.Notification;
import com.google.firebase.messaging.SendResponse;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Slice;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;

/**
 * Message Consumer 로,
 * RabbitMQ 에서 메시지를 꺼내 FCM 관련 로직 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
@Profile("worker")
public class FcmMessageConsumerImpl implements FcmMessageConsumer {

    private final FcmTokenRepository fcmTokenRepository;

    /**
     * 무효한 토큰으로 간주할 에러 코드들
     */
    private static final Set<String> DELETABLE_ERROR_CODES = Set.of(
            "unregistered",
            "invalid-argument",
            "invalid-arguments",
            "registration-token-not-registered",
            "messaging/invalid-registration-token",
            "messaging/registration-token-not-registered");

    /**
     * FCM 메시지 실제 처리 로직
     */
    @Override
    @Transactional
    public void processFcmMessage(FcmMessageRequest fcmMessage) {
        log.info("FCM 메시지 처리 시작: messageId={}, type={}",
                fcmMessage.getMessageId(), fcmMessage.getType());

        try {
            if (isStaleMessage(fcmMessage)) {
                return;
            }
            switch (fcmMessage.getType()) {
                case BROADCAST -> processBroadcastMessage(fcmMessage);
                case TEST -> processTestMessage(fcmMessage);
                case SINGLE -> processSingleMessage(fcmMessage);
            }

            log.info("FCM 메시지 처리 완료: messageId={}", fcmMessage.getMessageId());

        } catch (Exception e) {
            log.error("FCM 메시지 처리 실패: messageId={}, error={}",
                    fcmMessage.getMessageId(), e.getMessage(), e);
            throw new RuntimeException("FCM 메시지 처리 실패: " + fcmMessage.getMessageId(), e);
        }
    }

    /**
     * 메시지의 유효성(신선도)을 체크합니다.
     * SINGLE(채팅/하이 등) 알림은 5분이 지나면 UX를 위해 발송하지 않습니다.
     * BROADCAST는 정보 전달이 중요하므로 신선도 체크에서 제외(항상 유효)합니다.
     */
    private boolean isStaleMessage(FcmMessageRequest fcmMessage) {
        if (fcmMessage.getType() == FcmMessageRequest.FcmType.BROADCAST) {
            return false; // 브로드캐스트는 재처리를 위해 항상 유효함
        }

        java.time.LocalDateTime now = java.time.LocalDateTime.now();
        java.time.LocalDateTime createdAt = fcmMessage.getCreatedAt();

        if (createdAt != null && createdAt.isBefore(now.minusMinutes(5))) {
            log.info("신선도 만료로 알림 전송 스킵 (5분 경과): messageId={}, type={}, createdAt={}",
                    fcmMessage.getMessageId(), fcmMessage.getType(), createdAt);
            return true;
        }
        return false;
    }

    private void processBroadcastMessage(FcmMessageRequest fcmMessage) {
        // 페이징 기반 처리로 메모리 최적화
        int pageSize = 1000; // 한 번에 1000명씩 처리
        int pageNumber = 0;
        Slice<FcmToken> tokenSlice;

        do {
            tokenSlice = fcmTokenRepository.findAllByUserPushAgreeTrueSlice(
                    PageRequest.of(pageNumber++, pageSize));

            if (tokenSlice.isEmpty()) {
                log.info("브로드캐스트 대상 사용자가 없습니다.");
                return;
            }

            List<String> tokenStrings = tokenSlice.getContent().stream()
                    .map(FcmToken::getToken)
                    .filter(this::isValidToken)
                    .toList();

            // FCM Multicast는 한 번에 최대 500개까지 가능
            for (int i = 0; i < tokenStrings.size(); i += 500) {
                int end = Math.min(i + 500, tokenStrings.size());
                List<String> batch = tokenStrings.subList(i, end);
                sendMulticastMessage(batch, fcmMessage.getTitle(), fcmMessage.getBody());
            }

        } while (tokenSlice.hasNext());
    }

    private void sendMulticastMessage(List<String> tokens, String title, String body) {
        MulticastMessage message = MulticastMessage.builder()
                .addAllTokens(tokens)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .build();

        // 멀티캐스트도 재시도 로직 추가 (최대 3회)
        int maxRetry = 3;
        for (int attempt = 0; attempt < maxRetry; attempt++) {
            try {
                BatchResponse response = FirebaseMessaging.getInstance().sendEachForMulticast(message);
                if (response.getFailureCount() > 0) {
                    log.warn("FCM 멀티캐스트 전송 중 일부 실패: 총={}, 실패={}",
                            tokens.size(), response.getFailureCount());

                    // 실패한 토큰들에 대해 개별 처리 (무효 토큰 삭제 등)
                    handleBatchFailure(response, tokens);
                } else {
                    log.info("FCM 멀티캐스트 전송 성공: 총={}", tokens.size());
                }
                return; // 성공 시 종료

            } catch (FirebaseMessagingException e) {
                boolean isTransient = isTransientError(e);
                if (isTransient && attempt < maxRetry - 1) {
                    log.warn("FCM 멀티캐스트 일시적 오류로 재시도 중 ({}회차): error={}",
                            attempt + 1, e.getMessage());
                    try {
                        Thread.sleep(1000 * (attempt + 1));
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }

                log.error("FCM 멀티캐스트 전송 최종 실패: error={}", e.getMessage());
                throw new RuntimeException("FCM Multicast failed after retries", e);
            }
        }
    }

    private void handleBatchFailure(BatchResponse response, List<String> tokens) {
        List<SendResponse> responses = response.getResponses();
        for (int i = 0; i < responses.size(); i++) {
            if (!responses.get(i).isSuccessful()) {
                String token = tokens.get(i);
                FirebaseMessagingException e = responses.get(i).getException();
                log.warn("FCM 개별 전송 실패: token={}, error={}", maskToken(token), e.getMessage());

                // 무효한 토큰 삭제 처리 로직 (필요 시 DB 조회 후 삭제)
                fcmTokenRepository.findByToken(token).ifPresent(tokenEntity -> {
                    handleInvalidToken(e, tokenEntity, token, tokenEntity.getUser().getId());
                });
            }
        }
    }

    private void processSingleMessage(FcmMessageRequest fcmMessage) {
        if (fcmMessage.getUserId() == null) {
            log.warn("단일 메시지의 사용자 ID가 없습니다.");
            return;
        }

        // userId로 FCM 토큰 조회
        User user = User.builder().id(fcmMessage.getUserId()).build();
        FcmToken userToken = fcmTokenRepository.findByUser(user).orElse(null);

        if (userToken == null) {
            log.warn("사용자의 FCM 토큰을 찾을 수 없습니다: userId={}", fcmMessage.getUserId());
            return;
        }

        sendFcmMessage(userToken.getToken(), fcmMessage.getUserId(),
                fcmMessage.getTitle(), fcmMessage.getBody(), userToken);
    }

    private void processTestMessage(FcmMessageRequest fcmMessage) {
        List<String> fcmTokens = fcmMessage.getFcmTokens();

        if (fcmTokens == null || fcmTokens.isEmpty()) {
            log.warn("테스트 메시지의 FCM 토큰이 비어있습니다.");
            return;
        }

        String token = fcmTokens.get(0);
        sendFcmMessage(token, fcmMessage.getUserId(),
                fcmMessage.getTitle(), fcmMessage.getBody(), null);
    }

    private void sendFcmMessage(String token, Long userId, String title, String body, FcmToken tokenEntity) {
        if (!isValidToken(token)) {
            log.warn("FCM 토큰이 유효하지 않습니다: userId={}, token={}", userId,
                    token != null ? maskToken(token) : "null");
            return;
        }

        Message message = buildFcmMessage(token, title, body);

        // 재시도 로직 (최대 3회)
        int maxRetry = 3;
        for (int i = 0; i < maxRetry; i++) {
            try {
                String response = FirebaseMessaging.getInstance().send(message);
                log.info("FCM 전송 성공: userId={}, response={}", userId, response);
                return; // 성공 시 종료

            } catch (FirebaseMessagingException e) {
                boolean isTransient = isTransientError(e);
                if (isTransient && i < maxRetry - 1) {
                    log.warn("FCM 일시적 오류로 재시도 중 ({}회차): userId={}, error={}",
                            i + 1, userId, e.getMessage());
                    try {
                        Thread.sleep(1000 * (i + 1));
                    } catch (InterruptedException ignored) {
                    }
                    continue;
                }

                log.warn("FCM 전송 최종 실패: userId={}, error={}", userId, e.getMessage());
                if (tokenEntity != null) {
                    handleInvalidToken(e, tokenEntity, token, userId);
                }
                break;
            }
        }
    }

    private boolean isTransientError(FirebaseMessagingException e) {
        String code = e.getErrorCode().toString().toLowerCase();
        return code.contains("unavailable") || code.contains("internal") || code.contains("timeout");
    }

    private boolean isValidToken(String token) {
        return token != null && !token.isBlank() && !"null".equalsIgnoreCase(token);
    }

    private String maskToken(String token) {
        if (token == null || token.length() <= 10) {
            return "***";
        }
        return token.substring(0, 10) + "...";
    }

    private Message buildFcmMessage(String token, String title, String body) {
        return Message.builder()
                .setToken(token)
                .setNotification(Notification.builder()
                        .setTitle(title)
                        .setBody(body)
                        .build())
                .build();
    }

    private void handleInvalidToken(FirebaseMessagingException e, FcmToken tokenEntity, String token, Long userId) {
        String errorCode = e.getErrorCode() != null ? e.getErrorCode().toString().toLowerCase() : "";
        String errorMessage = e.getMessage() != null ? e.getMessage().toLowerCase() : "";

        // 에러 코드 또는 에러 메시지로 판단
        if (isDeleteableErrorCode(errorCode) || isDeleteableErrorMessage(errorMessage)) {
            fcmTokenRepository.delete(tokenEntity);
            log.warn("무효한 FCM 토큰 삭제: token={}, userId={}, errorCode={}, message={}",
                    maskToken(token), userId, errorCode, e.getMessage());
        }
    }

    private boolean isDeleteableErrorCode(String errorCode) {
        return DELETABLE_ERROR_CODES.contains(errorCode);
    }

    private boolean isDeleteableErrorMessage(String errorMessage) {
        return errorMessage.contains("unregistered") ||
                errorMessage.contains("not-registered") ||
                errorMessage.contains("invalid") && errorMessage.contains("token");
    }
}
