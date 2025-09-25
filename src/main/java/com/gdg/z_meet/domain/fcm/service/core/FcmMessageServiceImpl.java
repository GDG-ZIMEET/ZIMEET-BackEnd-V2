package com.gdg.z_meet.domain.fcm.service.core;

import com.gdg.z_meet.domain.fcm.service.producer.FcmMessageProducer;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmMessageServiceImpl implements FcmMessageService {

    private final UserRepository userRepository;
    private final FcmMessageProducer fcmMessageProducer;

    @Override
    public void broadcastToAllUsers(String title, String body) {
        log.info("브로드캐스트 FCM 메시지 큐 적재 요청: title={}", title);
        fcmMessageProducer.sendBroadcastMessage(title, body);
    }

    @Override
    public void testFcmService(Long userId, String fcmToken) {
        userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Code.USER_NOT_FOUND));

        log.info("테스트 FCM 메시지 큐 적재 요청: userId={}", userId);
        
        String title = "ZI-MEET FCM 알림 테스트입니다.";
        String body = "테스트 성공했나요?";
        
        fcmMessageProducer.sendTestMessage(userId, fcmToken, title, body);
    }
}