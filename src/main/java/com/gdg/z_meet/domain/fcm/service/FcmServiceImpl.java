package com.gdg.z_meet.domain.fcm.service;

import com.gdg.z_meet.domain.fcm.entity.FcmToken;
import com.gdg.z_meet.domain.fcm.repository.FcmTokenRepository;
import com.gdg.z_meet.domain.user.dto.UserReq;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import com.google.firebase.messaging.Notification;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Set;


@Service
@RequiredArgsConstructor
@Slf4j
public class FcmServiceImpl implements FcmService {

    private final UserRepository userRepository;
    private final FcmTokenRepository fcmTokenRepository;


    @Override
    @Transactional
    public boolean agreePush(Long userId, UserReq.pushAgreeReq req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Code.USER_NOT_FOUND));

        user.setPushAgree(req.isPushAgree());
        return req.isPushAgree();
    }

    @Override
    @Transactional
    public void syncFcmToken(Long userId, UserReq.saveFcmTokenReq req) {

        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Code.USER_NOT_FOUND));

        if (!user.isPushAgree()) { throw new BusinessException(Code.FCM_PUSH_NOT_AGREED);}

        String newToken = req.getFcmToken();
        FcmToken token = fcmTokenRepository.findByUser(user).orElse(null);

        if (token == null) {
            fcmTokenRepository.save(FcmToken.builder()
                    .user(user)
                    .token(newToken)
                    .build());
            return;
        }

        // 토큰이 다를 때만 갱신
        if (!newToken.equals(token.getToken())) {
            token.setToken(newToken);
        }
    }


    @Transactional
    public void broadcastToAllUsers(String title, String body) {

        List<FcmToken> tokens = fcmTokenRepository.findAllByUserPushAgreeTrue();  // 푸시 수신 동의 사용자만

        for (FcmToken userToken : tokens) {
            String token = userToken.getToken();
            Long userId = userToken.getUser().getId();

            if (token == null || token.isBlank() || "null".equalsIgnoreCase(token)) {
                log.warn("FCM 브로드캐스트 대상 토큰이 비어 있음 또는 'null' 문자열: tokenEntityId={},  userId={}", userToken.getId(), userId);
                continue;
            }

            Message message = Message.builder()
                    .setToken(token)
                    .setNotification(Notification.builder()
                            .setTitle(title)
                            .setBody(body)
                            .build())
                    .build();

            try {
                String response = FirebaseMessaging.getInstance().send(message);
                log.info("FCM 전송 성공 (브로드캐스트): userId={}, response={}", userId, response);
            } catch (FirebaseMessagingException e) {
                log.warn("FCM 전송 실패 (브로드캐스트): userId={}, error={}", userId, e.getMessage(), e);

                Set<String> deletableErrorCodes = Set.of(
                        "unregistered",
                        "invalid_argument", "invalid_arguments",
                        "registration-token-not-registered",
                        "messaging/invalid-registration-token"
                );

                String errorCode = String.valueOf(e.getErrorCode());
                if (errorCode != null && deletableErrorCodes.contains(errorCode.toLowerCase())) {
                    fcmTokenRepository.delete(userToken);
                    log.warn("무효한 FCM 토큰 삭제: token={}, userId={}", token, userId);
                }
            }
        }
    }



    @Override
    public void testFcmService(Long userId, String fcmToken) {

        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Code.USER_NOT_FOUND));

        log.info("받은 FCM 토큰 값 : " + fcmToken);

        String title = "ZI-MEET FCM 알림 테스트입니다.";
        String body = "테스트 성공했나요?";

        Notification notification = Notification.builder()
                .setTitle(title)
                .setBody(body)
                .build();

        Message message = Message.builder()
                .setToken(fcmToken)
                .setNotification(notification)
                .build();

        log.info("📨 FCM 메시지 제목: {}", title);
        log.info("📨 FCM 메시지 내용: {}", body);

        try {
            String response = FirebaseMessaging.getInstance().send(message);
            log.info("FCM 응답: {}", response);
        } catch (FirebaseMessagingException e) {
            log.warn("FCM 전송 실패: {}", e.getMessage(), e);
            throw new BusinessException(Code.FCM_SEND_MESSAGE_ERROR);
        }
    }
}
