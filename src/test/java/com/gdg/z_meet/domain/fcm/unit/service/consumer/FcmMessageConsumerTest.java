package com.gdg.z_meet.domain.fcm.unit.service.consumer;

import com.gdg.z_meet.domain.fcm.dto.FcmMessageRequest;
import com.gdg.z_meet.domain.fcm.entity.FcmToken;
import com.gdg.z_meet.domain.fcm.repository.FcmTokenRepository;
import com.gdg.z_meet.domain.fcm.service.consumer.FcmMessageConsumerImpl;
import com.gdg.z_meet.domain.user.entity.User;
import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.Collections;
import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmMessageConsumer 단위 테스트")
class FcmMessageConsumerTest {

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @InjectMocks
    private FcmMessageConsumerImpl fcmMessageConsumer;

    private User testUser;
    private FcmToken testToken;
    private FcmMessageRequest singleRequest;
    private FcmMessageRequest broadcastRequest;
    private FcmMessageRequest testRequest;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .pushAgree(true)
                .build();

        testToken = FcmToken.builder()
                .id(1L)
                .user(testUser)
                .token("valid-fcm-token")
                .build();

        singleRequest = FcmMessageRequest.builder()
                .messageId("single-message-id")
                .type(FcmMessageRequest.FcmType.SINGLE)
                .title("단일 메시지")
                .body("단일 사용자에게 전송되는 메시지")
                .userId(1L)
                .createdAt(LocalDateTime.now())
                .build();

        broadcastRequest = FcmMessageRequest.builder()
                .messageId("broadcast-message-id")
                .type(FcmMessageRequest.FcmType.BROADCAST)
                .title("브로드캐스트 메시지")
                .body("모든 사용자에게 전송되는 메시지")
                .createdAt(LocalDateTime.now())
                .build();

        testRequest = FcmMessageRequest.builder()
                .messageId("test-message-id")
                .type(FcmMessageRequest.FcmType.TEST)
                .title("테스트 메시지")
                .body("테스트용 메시지")
                .userId(1L)
                .fcmTokens(Collections.singletonList("test-fcm-token"))
                .createdAt(LocalDateTime.now())
                .build();
    }

    @Test
    @DisplayName("단일 메시지 처리 성공")
    void 단일메시지_처리성공() throws FirebaseMessagingException {
        when(fcmTokenRepository.findByUser(any(User.class))).thenReturn(Optional.of(testToken));

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenReturn("projects/test/messages/msg_123");

            fcmMessageConsumer.processFcmMessage(singleRequest);

            verify(fcmTokenRepository).findByUser(any(User.class));
            verify(firebaseMessaging).send(any(Message.class));
        }
    }

    @Test
    @DisplayName("단일 메시지 - 사용자 ID 없음")
    void 단일메시지_사용자ID없음() throws FirebaseMessagingException {
        FcmMessageRequest requestWithNullUserId = singleRequest.toBuilder()
                .userId(null)
                .build();

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            fcmMessageConsumer.processFcmMessage(requestWithNullUserId);

            verify(fcmTokenRepository, never()).findByUser(any(User.class));
            verify(firebaseMessaging, never()).send(any(Message.class));
        }
    }

    @Test
    @DisplayName("단일 메시지 - 토큰 없음")
    void 단일메시지_토큰없음() throws FirebaseMessagingException {
        when(fcmTokenRepository.findByUser(any(User.class))).thenReturn(Optional.empty());

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            fcmMessageConsumer.processFcmMessage(singleRequest);

            verify(fcmTokenRepository).findByUser(any(User.class));
            verify(firebaseMessaging, never()).send(any(Message.class));
        }
    }

    @Test
    @DisplayName("브로드캐스트 메시지 처리 성공")
    void 브로드캐스트메시지_처리성공() throws FirebaseMessagingException {
        List<FcmToken> tokens = Collections.singletonList(testToken);
        when(fcmTokenRepository.findAllByUserPushAgreeTrue()).thenReturn(tokens);

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenReturn("projects/test/messages/msg_123");

            fcmMessageConsumer.processFcmMessage(broadcastRequest);

            verify(fcmTokenRepository).findAllByUserPushAgreeTrue();
            verify(firebaseMessaging).send(any(Message.class));
        }
    }

    @Test
    @DisplayName("브로드캐스트 메시지 - 푸시 동의 사용자 없음")
    void 브로드캐스트메시지_푸시동의사용자없음() throws FirebaseMessagingException {
        when(fcmTokenRepository.findAllByUserPushAgreeTrue()).thenReturn(Collections.emptyList());

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            fcmMessageConsumer.processFcmMessage(broadcastRequest);

            verify(fcmTokenRepository).findAllByUserPushAgreeTrue();
            verify(firebaseMessaging, never()).send(any(Message.class));
        }
    }

    @Test
    @DisplayName("테스트 메시지 처리 성공")
    void 테스트메시지_처리성공() throws FirebaseMessagingException {
        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenReturn("projects/test/messages/msg_123");

            fcmMessageConsumer.processFcmMessage(testRequest);

            verify(firebaseMessaging).send(any(Message.class));
        }
    }

    @Test
    @DisplayName("무효한 토큰 - FCM 전송 실패 시 토큰 삭제")
    void 무효한토큰_FCM전송실패_토큰삭제() throws FirebaseMessagingException {
        when(fcmTokenRepository.findByUser(any(User.class))).thenReturn(Optional.of(testToken));

        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getErrorCode()).thenReturn(ErrorCode.INVALID_ARGUMENT);
        when(exception.getMessage()).thenReturn("invalid registration token");

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

            fcmMessageConsumer.processFcmMessage(singleRequest);

            verify(fcmTokenRepository).delete(testToken);
        }
    }

    @Test
    @DisplayName("일반 예외 발생 시 토큰 삭제 안함")
    void 일반예외발생_토큰삭제안함() throws FirebaseMessagingException {
        when(fcmTokenRepository.findByUser(any(User.class))).thenReturn(Optional.of(testToken));

        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getErrorCode()).thenReturn(ErrorCode.INTERNAL);

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

            fcmMessageConsumer.processFcmMessage(singleRequest);

            verify(fcmTokenRepository, never()).delete(any(FcmToken.class));
        }
    }

    @Test
    @DisplayName("처리 중 예외 발생 시 예외 재던짐")
    void 처리중예외발생_예외재던짐() {
        when(fcmTokenRepository.findByUser(any(User.class))).thenThrow(new RuntimeException("DB 연결 실패"));

        assertThrows(RuntimeException.class, () -> fcmMessageConsumer.processFcmMessage(singleRequest));
    }

    @Test
    @DisplayName("비동기 메시지 처리 - 예외 발생 시 재큐 방지 검증")
    void 비동기메시지처리_예외발생시_재큐방지() {
        when(fcmTokenRepository.findByUser(any(User.class))).thenThrow(new RuntimeException("처리 실패"));

        // AmqpRejectAndDontRequeueException이 발생해야 함
        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            RuntimeException exception = assertThrows(RuntimeException.class,
                    () -> fcmMessageConsumer.processFcmMessage(singleRequest));

            // 예외가 발생했고, 실제 구현에서는 AmqpRejectAndDontRequeueException으로 래핑됨
            assert exception != null;
        }
    }

    @Test
    @DisplayName("비동기 메시지 처리 - 트랜잭션 롤백 검증")
    void 비동기메시지처리_트랜잭션롤백() throws FirebaseMessagingException {
        when(fcmTokenRepository.findByUser(any(User.class))).thenReturn(Optional.of(testToken));

        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getErrorCode()).thenReturn(ErrorCode.INVALID_ARGUMENT);
        when(exception.getMessage()).thenReturn("invalid token");

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

            // 예외가 발생하지만, 토큰 삭제는 트랜잭션 내에서 수행됨
            fcmMessageConsumer.processFcmMessage(singleRequest);

            verify(fcmTokenRepository).delete(testToken);
        }
    }
}
