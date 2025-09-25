package com.gdg.z_meet.domain.fcm.service.consumer;

import com.gdg.z_meet.domain.fcm.dto.FcmMessageRequest;
import com.gdg.z_meet.domain.fcm.entity.FcmToken;
import com.gdg.z_meet.domain.fcm.repository.FcmTokenRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.google.firebase.ErrorCode;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.FirebaseMessagingException;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.BeforeEach;
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
                .studentNumber("12345678")
                .name("테스트사용자")
                .phoneNumber("010-1234-5678")
                .password("password")
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
    void 단일메시지_사용자ID_null() throws FirebaseMessagingException {
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
    void 테스트메시지_처리성공() throws FirebaseMessagingException {
        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenReturn("projects/test/messages/msg_123");

            fcmMessageConsumer.processFcmMessage(testRequest);

            verify(firebaseMessaging).send(any(Message.class));
        }
    }

    @Test
    void 테스트메시지_FCM토큰없음() throws FirebaseMessagingException {
        FcmMessageRequest emptyTokenRequest = testRequest.toBuilder()
                .fcmTokens(Collections.emptyList())
                .build();

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            fcmMessageConsumer.processFcmMessage(emptyTokenRequest);

            verify(firebaseMessaging, never()).send(any(Message.class));
        }
    }

    @Test
    void 테스트메시지_FCM토큰_null() throws FirebaseMessagingException {
        FcmMessageRequest nullTokenRequest = testRequest.toBuilder()
                .fcmTokens(null)
                .build();

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            fcmMessageConsumer.processFcmMessage(nullTokenRequest);

            verify(firebaseMessaging, never()).send(any(Message.class));
        }
    }

    @Test
    void 무효한토큰_FCM전송실패_토큰삭제() throws FirebaseMessagingException {
        when(fcmTokenRepository.findByUser(any(User.class))).thenReturn(Optional.of(testToken));

        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getErrorCode()).thenReturn(ErrorCode.INVALID_ARGUMENT);

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

            fcmMessageConsumer.processFcmMessage(singleRequest);

            verify(fcmTokenRepository).delete(testToken);
        }
    }

    @Test
    void 빈토큰또는Null토큰_메시지전송안함() throws FirebaseMessagingException {
        FcmToken emptyToken = FcmToken.builder()
                .id(1L)
                .user(testUser)
                .token("")
                .build();

        when(fcmTokenRepository.findByUser(any(User.class))).thenReturn(Optional.of(emptyToken));

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            fcmMessageConsumer.processFcmMessage(singleRequest);

            verify(firebaseMessaging, never()).send(any(Message.class));
        }
    }

    @Test
    void 문자열null토큰_메시지전송안함() throws FirebaseMessagingException {
        FcmToken nullStringToken = FcmToken.builder()
                .id(1L)
                .user(testUser)
                .token("null")
                .build();

        when(fcmTokenRepository.findByUser(any(User.class))).thenReturn(Optional.of(nullStringToken));

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            fcmMessageConsumer.processFcmMessage(singleRequest);

            verify(firebaseMessaging, never()).send(any(Message.class));
        }
    }

    @Test
    void FCM전송_일반예외발생_토큰삭제안함() throws FirebaseMessagingException {
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
    void 처리중예외발생_예외재던짐() {
        when(fcmTokenRepository.findByUser(any(User.class))).thenThrow(new RuntimeException("DB 연결 실패"));

        assertThrows(RuntimeException.class, () -> fcmMessageConsumer.processFcmMessage(singleRequest));
    }
}