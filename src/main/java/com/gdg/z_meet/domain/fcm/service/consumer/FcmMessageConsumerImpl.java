package com.gdg.z_meet.domain.fcm.service.consumer;

import com.gdg.z_meet.domain.fcm.dto.FcmMessageRequest;
import com.gdg.z_meet.domain.fcm.entity.FcmToken;
import com.gdg.z_meet.domain.fcm.repository.FcmTokenRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.global.config.RabbitMqConfig;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Set;

/**
 *  Message Consumer 로,
 *  RabbitMQ 에서 메시지를 꺼내 FCM 관련 로직 처리
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FcmMessageConsumerImpl implements FcmMessageConsumer {

    private final FcmTokenRepository fcmTokenRepository;

    /**
     *  무효한 토큰으로 간주할 에러 코드들
     */
    private static final Set<String> DELETABLE_ERROR_CODES = Set.of(
            "unregistered",
            "invalid_argument", 
            "invalid_arguments",
            "registration-token-not-registered",
            "messaging/invalid-registration-token"
    );

    /**
     *  FCM 전용 큐에 바인딩
     *  @RabbitListener(queues = RabbitMqConfig.FCM_QUEUE) : Spring 이 알아서 메시지를 꺼냄 → JSON 역직렬화 → fcmMessage 객체로 바인딩 → 메서드 실행
     */
    @RabbitListener(queues = RabbitMqConfig.FCM_QUEUE)
    @Transactional
    @Override
    public void processFcmMessage(FcmMessageRequest fcmMessage) {
        log.info("FCM 메시지 처리 시작: messageId={}, type={}", 
                fcmMessage.getMessageId(), fcmMessage.getType());


        try {
            switch (fcmMessage.getType()) {
                case BROADCAST -> processBroadcastMessage(fcmMessage);
                case TEST -> processTestMessage(fcmMessage);
                case SINGLE -> processSingleMessage(fcmMessage);
            }
            
            log.info("FCM 메시지 처리 완료: messageId={}", fcmMessage.getMessageId());
            
        } catch (Exception e) {
            log.error("FCM 메시지 처리 실패: messageId={}, error={}", 
                    fcmMessage.getMessageId(), e.getMessage(), e);
            throw e;
        }
    }

    private void processBroadcastMessage(FcmMessageRequest fcmMessage) {
        List<FcmToken> tokens = fcmTokenRepository.findAllByUserPushAgreeTrue();
        
        if (tokens.isEmpty()) {
            log.info("브로드캐스트 대상 사용자가 없습니다.");
            return;
        }
        
        for (FcmToken userToken : tokens) {
            sendFcmMessage(userToken.getToken(), userToken.getUser().getId(), 
                         fcmMessage.getTitle(), fcmMessage.getBody(), userToken);
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

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM 전송 성공: userId={}, response={}", userId, response);
            
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 전송 실패: userId={}, error={}", userId, e.getMessage());

            // 무효한 토큰인 경우 삭제 처리
            if (tokenEntity != null) {
                handleInvalidToken(e, tokenEntity, token, userId);
            }
        }
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
        String errorCode = e.getErrorCode() != null ? e.getErrorCode().toString() : "";
        
        if (isDeleteableErrorCode(errorCode)) {
            fcmTokenRepository.delete(tokenEntity);
            log.warn("무효한 FCM 토큰 삭제: token={}, userId={}, errorCode={}", 
                    maskToken(token), userId, errorCode);
        }
    }
    
    private boolean isDeleteableErrorCode(String errorCode) {
        return DELETABLE_ERROR_CODES.contains(errorCode.toLowerCase());
    }
}
