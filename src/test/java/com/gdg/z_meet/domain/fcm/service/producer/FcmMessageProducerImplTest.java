package com.gdg.z_meet.domain.fcm.service.producer;

import com.gdg.z_meet.domain.fcm.dto.FcmMessageRequest;
import com.gdg.z_meet.global.config.RabbitMqConfig;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;

@ExtendWith(MockitoExtension.class)
class FcmMessageProducerImplTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private FcmMessageProducerImpl fcmMessageProducer;

    @Test
    void 브로드캐스트메시지_전송성공() {
        String title = "브로드캐스트 제목";
        String body = "브로드캐스트 내용";

        fcmMessageProducer.sendBroadcastMessage(title, body);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.FCM_EXCHANGE),
                eq(RabbitMqConfig.FCM_ROUTING_KEY),
                argThat((FcmMessageRequest message) -> {
                    return message.getType() == FcmMessageRequest.FcmType.BROADCAST &&
                            message.getTitle().equals(title) &&
                            message.getBody().equals(body) &&
                            message.getMessageId() != null &&
                            message.getCreatedAt() != null;
                })
        );
    }

    @Test
    void 테스트메시지_전송성공() {
        Long userId = 1L;
        String fcmToken = "test-fcm-token";
        String title = "테스트 제목";
        String body = "테스트 내용";

        fcmMessageProducer.sendTestMessage(userId, fcmToken, title, body);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.FCM_EXCHANGE),
                eq(RabbitMqConfig.FCM_ROUTING_KEY),
                argThat((FcmMessageRequest message) -> {
                    return message.getType() == FcmMessageRequest.FcmType.TEST &&
                            message.getTitle().equals(title) &&
                            message.getBody().equals(body) &&
                            message.getUserId().equals(userId) &&
                            message.getFcmTokens().equals(List.of(fcmToken)) &&
                            message.getMessageId() != null &&
                            message.getCreatedAt() != null;
                })
        );
    }

    @Test
    void 단일메시지_전송성공() {
        Long userId = 1L;
        String title = "단일 메시지 제목";
        String body = "단일 메시지 내용";

        fcmMessageProducer.sendSingleMessage(userId, title, body);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.FCM_EXCHANGE),
                eq(RabbitMqConfig.FCM_ROUTING_KEY),
                argThat((FcmMessageRequest message) -> {
                    return message.getType() == FcmMessageRequest.FcmType.SINGLE &&
                            message.getTitle().equals(title) &&
                            message.getBody().equals(body) &&
                            message.getUserId().equals(userId) &&
                            message.getMessageId() != null &&
                            message.getCreatedAt() != null;
                })
        );
    }

    @Test
    void 빈문자열_제목과내용으로_메시지전송() {
        String emptyTitle = "";
        String emptyBody = "";

        fcmMessageProducer.sendBroadcastMessage(emptyTitle, emptyBody);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.FCM_EXCHANGE),
                eq(RabbitMqConfig.FCM_ROUTING_KEY),
                argThat((FcmMessageRequest message) -> {
                    return message.getTitle().equals(emptyTitle) &&
                            message.getBody().equals(emptyBody);
                })
        );
    }

    @Test
    void null_userId로_테스트메시지전송() {
        Long userId = null;
        String fcmToken = "test-token";
        String title = "제목";
        String body = "내용";

        fcmMessageProducer.sendTestMessage(userId, fcmToken, title, body);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.FCM_EXCHANGE),
                eq(RabbitMqConfig.FCM_ROUTING_KEY),
                argThat((FcmMessageRequest message) -> {
                    return message.getUserId() == null &&
                            message.getFcmTokens().equals(List.of(fcmToken));
                })
        );
    }
}