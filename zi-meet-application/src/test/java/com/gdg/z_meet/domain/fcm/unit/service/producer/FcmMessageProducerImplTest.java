package com.gdg.z_meet.domain.fcm.unit.service.producer;

import com.gdg.z_meet.domain.fcm.dto.FcmMessageRequest;
import com.gdg.z_meet.domain.fcm.event.FcmMessageEvent;
import com.gdg.z_meet.domain.fcm.service.producer.FcmMessageProducerImpl;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.context.ApplicationEventPublisher;

import java.util.List;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.times;

@ExtendWith(MockitoExtension.class)
class FcmMessageProducerImplTest {

    @Mock
    private ApplicationEventPublisher eventPublisher;

    @InjectMocks
    private FcmMessageProducerImpl fcmMessageProducer;

    @Test
    void 브로드캐스트메시지_전송성공() {
        String title = "브로드캐스트 제목";
        String body = "브로드캐스트 내용";

        fcmMessageProducer.sendBroadcastMessage(title, body);

        verify(eventPublisher).publishEvent(argThat((FcmMessageEvent event) -> {
            FcmMessageRequest message = event.getMessage();
            return message.getType() == FcmMessageRequest.FcmType.BROADCAST &&
                    message.getTitle().equals(title) &&
                    message.getBody().equals(body) &&
                    message.getMessageId() != null;
        }));
    }

    @Test
    void 테스트메시지_전송성공() {
        Long userId = 1L;
        String fcmToken = "test-fcm-token";
        String title = "테스트 제목";
        String body = "테스트 내용";

        fcmMessageProducer.sendTestMessage(userId, fcmToken, title, body);

        verify(eventPublisher).publishEvent(argThat((FcmMessageEvent event) -> {
            FcmMessageRequest message = event.getMessage();
            return message.getType() == FcmMessageRequest.FcmType.TEST &&
                    message.getTitle().equals(title) &&
                    message.getBody().equals(body) &&
                    message.getUserId().equals(userId) &&
                    message.getFcmTokens().equals(List.of(fcmToken));
        }));
    }

    @Test
    void 단일메시지_전송성공() {
        Long userId = 1L;
        String title = "단일 메시지 제목";
        String body = "단일 메시지 내용";

        fcmMessageProducer.sendSingleMessage(userId, title, body);

        verify(eventPublisher).publishEvent(argThat((FcmMessageEvent event) -> {
            FcmMessageRequest message = event.getMessage();
            return message.getType() == FcmMessageRequest.FcmType.SINGLE &&
                    message.getTitle().equals(title) &&
                    message.getBody().equals(body) &&
                    message.getUserId().equals(userId);
        }));
    }

    @Test
    void 메시지ID_유니크성_검증() {
        String title = "제목";
        String body = "내용";

        fcmMessageProducer.sendBroadcastMessage(title, body);
        fcmMessageProducer.sendBroadcastMessage(title, body);

        verify(eventPublisher, times(2)).publishEvent(any(FcmMessageEvent.class));
    }
}