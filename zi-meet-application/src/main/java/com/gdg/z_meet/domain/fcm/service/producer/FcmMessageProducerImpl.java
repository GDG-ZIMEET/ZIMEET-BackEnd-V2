package com.gdg.z_meet.domain.fcm.service.producer;

import com.gdg.z_meet.domain.fcm.dto.FcmMessageRequest;
import com.gdg.z_meet.domain.fcm.event.FcmMessageEvent;
import com.gdg.z_meet.global.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.UUID;

/**
 * Message Publish Producer 로,
 * 직접 전송 대신 내부 이벤트를 발행하여 트랜잭션 커밋 이후 전송되도록 처리한다.
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FcmMessageProducerImpl implements FcmMessageProducer {

        private final ApplicationEventPublisher eventPublisher;

        @Override
        public void sendBroadcastMessage(String title, String body) {

                FcmMessageRequest message = FcmMessageRequest.builder()
                                .messageId(UUID.randomUUID().toString())
                                .type(FcmMessageRequest.FcmType.BROADCAST)
                                .title(title)
                                .body(body)
                                .createdAt(LocalDateTime.now())
                                .build();

                eventPublisher.publishEvent(new FcmMessageEvent(
                                RabbitMqConfig.FCM_BROADCAST_ROUTING_KEY,
                                message));

                log.info("브로드캐스트 FCM 메시지 이벤트를 발행했습니다. messageId: {}, title: {}",
                                message.getMessageId(), title);
        }

        @Override
        public void sendSingleMessage(Long userId, String title, String body) {
                FcmMessageRequest message = FcmMessageRequest.builder()
                                .messageId(UUID.randomUUID().toString())
                                .type(FcmMessageRequest.FcmType.SINGLE)
                                .title(title)
                                .body(body)
                                .userId(userId)
                                .createdAt(LocalDateTime.now())
                                .build();

                eventPublisher.publishEvent(new FcmMessageEvent(
                                RabbitMqConfig.FCM_SINGLE_ROUTING_KEY,
                                message));

                log.info("단일 FCM 메시지 이벤트를 발행했습니다. messageId: {}, userId: {}",
                                message.getMessageId(), userId);
        }

        @Override
        public void sendTestMessage(Long userId, String fcmToken, String title, String body) {

                if (fcmToken == null || fcmToken.isBlank()) {
                        log.warn("테스트 FCM 메시지 이벤트를 발행하지 않습니다. userId={}, 사유=FCM 토큰 누락", userId);
                        return;
                }

                FcmMessageRequest message = FcmMessageRequest.builder()
                                .messageId(UUID.randomUUID().toString())
                                .type(FcmMessageRequest.FcmType.TEST)
                                .title(title)
                                .body(body)
                                .userId(userId)
                                .fcmTokens(List.of(fcmToken))
                                .createdAt(LocalDateTime.now())
                                .build();

                eventPublisher.publishEvent(new FcmMessageEvent(
                                RabbitMqConfig.FCM_SINGLE_ROUTING_KEY,
                                message));

                log.info("테스트 FCM 메시지 이벤트를 발행했습니다. messageId: {}, userId: {}",
                                message.getMessageId(), userId);
        }
}
