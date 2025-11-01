package com.gdg.z_meet.domain.fcm.unit.service.producer;

import com.gdg.z_meet.domain.fcm.dto.FcmMessageRequest;
import com.gdg.z_meet.domain.fcm.service.producer.FcmMessageProducerImpl;
import com.gdg.z_meet.global.config.RabbitMqConfig;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.rabbit.core.RabbitTemplate;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmMessageProducer 단위 테스트")
class FcmMessageProducerImplTest {

    @Mock
    private RabbitTemplate rabbitTemplate;

    @InjectMocks
    private FcmMessageProducerImpl fcmMessageProducer;

    @Test
    @DisplayName("브로드캐스트 메시지 전송 성공")
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
                            message.getMessageId() != null;
                })
        );
    }

    @Test
    @DisplayName("단일 메시지 전송 성공")
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
                            message.getUserId().equals(userId);
                })
        );
    }

    @Test
    @DisplayName("테스트 메시지 전송 성공")
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
                            message.getFcmTokens().equals(List.of(fcmToken)) &&
                            message.getUserId().equals(userId);
                })
        );
    }

    @Test
    @DisplayName("빈 FCM 토큰으로 테스트 메시지 전송 안함")
    void 빈토큰_테스트메시지_전송안함() {
        Long userId = 1L;
        String fcmToken = "";

        fcmMessageProducer.sendTestMessage(userId, fcmToken, "제목", "내용");

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(FcmMessageRequest.class));
    }

    @Test
    @DisplayName("null FCM 토큰으로 테스트 메시지 전송 안함")
    void null토큰_테스트메시지_전송안함() {
        Long userId = 1L;
        String fcmToken = null;

        fcmMessageProducer.sendTestMessage(userId, fcmToken, "제목", "내용");

        verify(rabbitTemplate, never()).convertAndSend(anyString(), anyString(), any(FcmMessageRequest.class));
    }

    @Test
    @DisplayName("RabbitMQ 연결 실패 시 예외 전파")
    void RabbitMQ연결실패_예외전파() {
        String title = "제목";
        String body = "내용";

        doThrow(new RuntimeException("RabbitMQ 연결 실패"))
                .when(rabbitTemplate).convertAndSend(anyString(), anyString(), any(FcmMessageRequest.class));

        assertThrows(RuntimeException.class,
                () -> fcmMessageProducer.sendBroadcastMessage(title, body));
    }

    @Test
    @DisplayName("비동기 메시지 발행 - 메시지 ID 자동 생성 검증")
    void 비동기메시지발행_메시지ID자동생성() {
        String title = "제목";
        String body = "내용";

        fcmMessageProducer.sendBroadcastMessage(title, body);

        verify(rabbitTemplate).convertAndSend(
                eq(RabbitMqConfig.FCM_EXCHANGE),
                eq(RabbitMqConfig.FCM_ROUTING_KEY),
                argThat((FcmMessageRequest message) -> {
                    return message.getMessageId() != null &&
                            !message.getMessageId().isEmpty();
                })
        );
    }
}
