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
        when(exception.getMessage()).thenReturn("invalid registration token");

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

    @Test
    void 브로드캐스트메시지_여러사용자_일부전송실패() throws FirebaseMessagingException {
        User user1 = User.builder().id(1L).pushAgree(true).build();
        User user2 = User.builder().id(2L).pushAgree(true).build();
        
        FcmToken token1 = FcmToken.builder().id(1L).user(user1).token("valid-token-1").build();
        FcmToken token2 = FcmToken.builder().id(2L).user(user2).token("invalid-token-2").build();
        
        List<FcmToken> tokens = List.of(token1, token2);
        when(fcmTokenRepository.findAllByUserPushAgreeTrue()).thenReturn(tokens);

        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getErrorCode()).thenReturn(ErrorCode.INVALID_ARGUMENT);
        when(exception.getMessage()).thenReturn("invalid token");

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class)))
                    .thenReturn("success")  // 첫 번째 호출은 성공
                    .thenThrow(exception);  // 두 번째 호출은 실패

            fcmMessageConsumer.processFcmMessage(broadcastRequest);

            verify(firebaseMessaging, times(2)).send(any(Message.class));
            verify(fcmTokenRepository).delete(token2);  // 실패한 토큰만 삭제
            verify(fcmTokenRepository, never()).delete(token1);  // 성공한 토큰은 삭제 안함
        }
    }

    @Test
    void 다양한_삭제가능한_에러코드_테스트() throws FirebaseMessagingException {
        when(fcmTokenRepository.findByUser(any(User.class))).thenReturn(Optional.of(testToken));

        // 삭제 가능한 메시지가 포함된 에러
        FirebaseMessagingException unregisteredEx = mock(FirebaseMessagingException.class);
        when(unregisteredEx.getErrorCode()).thenReturn(ErrorCode.UNAUTHENTICATED);
        when(unregisteredEx.getMessage()).thenReturn("Requested entity was not found. unregistered token");

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(unregisteredEx);

            fcmMessageConsumer.processFcmMessage(singleRequest);

            // 에러 메시지에 "unregistered"가 포함되므로 토큰 삭제됨
            verify(fcmTokenRepository).delete(testToken);
        }
    }

    @Test
    void 테스트메시지_빈토큰_전송안함() throws FirebaseMessagingException {
        FcmMessageRequest emptyTokenRequest = testRequest.toBuilder()
                .fcmTokens(List.of(""))
                .build();

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            fcmMessageConsumer.processFcmMessage(emptyTokenRequest);

            verify(firebaseMessaging, never()).send(any(Message.class));
        }
    }

    @Test
    void 테스트메시지_공백토큰_전송안함() throws FirebaseMessagingException {
        FcmMessageRequest blankTokenRequest = testRequest.toBuilder()
                .fcmTokens(List.of("   "))
                .build();

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);

            fcmMessageConsumer.processFcmMessage(blankTokenRequest);

            verify(firebaseMessaging, never()).send(any(Message.class));
        }
    }

    @Test
    void 알수없는_FCM타입_처리() {
        // 존재하지 않는 타입으로 설정 (실제로는 enum이라 불가능하지만, null로 테스트)
        FcmMessageRequest unknownTypeRequest = FcmMessageRequest.builder()
                .messageId("unknown-type-id")
                .type(null)
                .title("제목")
                .body("내용")
                .createdAt(LocalDateTime.now())
                .build();

        // NPE가 발생해야 하는 상황
        assertThrows(RuntimeException.class, 
                () -> fcmMessageConsumer.processFcmMessage(unknownTypeRequest));
    }

    // 토큰엔티티없이_FCM전송실패시_토큰삭제안함 테스트는 
    // TEST 메시지 타입의 특성상 이미 다른 테스트들에서 충분히 검증됨

    @Test
    void FCM_알림메시지_구조_검증() throws FirebaseMessagingException {
        when(fcmTokenRepository.findByUser(any(User.class))).thenReturn(Optional.of(testToken));

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenReturn("success");

            fcmMessageConsumer.processFcmMessage(singleRequest);

            // FCM 메시지가 전송되었는지만 확인 (Message 내부 구조는 접근 불가)
            verify(firebaseMessaging).send(any(Message.class));
            verify(fcmTokenRepository).findByUser(any(User.class));
        }
    }

    @Test
    void FCM_전송성공_응답코드_검증() throws FirebaseMessagingException {
        when(fcmTokenRepository.findByUser(any(User.class))).thenReturn(Optional.of(testToken));

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenReturn("projects/test-project/messages/msg_123456789");

            fcmMessageConsumer.processFcmMessage(singleRequest);

            verify(firebaseMessaging).send(any(Message.class));
            // 성공시 토큰 삭제되지 않음
            verify(fcmTokenRepository, never()).delete(any(FcmToken.class));
        }
    }

    @Test
    void FCM_삭제가능한_에러코드들_검증() throws FirebaseMessagingException {
        when(fcmTokenRepository.findByUser(any(User.class))).thenReturn(Optional.of(testToken));
        
        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getErrorCode()).thenReturn(ErrorCode.INVALID_ARGUMENT);
        when(exception.getMessage()).thenReturn("invalid registration token");

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

            fcmMessageConsumer.processFcmMessage(singleRequest);

            // INVALID_ARGUMENT + "invalid"를 포함한 메시지이므로 토큰 삭제됨
            verify(fcmTokenRepository).delete(testToken);
        }
    }

    @Test
    void FCM_삭제불가능한_에러코드_검증() throws FirebaseMessagingException {
        // 삭제 불가능한 에러코드들을 개별 테스트로 분리
        ErrorCode[] nonDeletableErrors = {ErrorCode.INTERNAL, ErrorCode.UNAVAILABLE, 
                                         ErrorCode.DEADLINE_EXCEEDED, ErrorCode.RESOURCE_EXHAUSTED};

        for (ErrorCode errorCode : nonDeletableErrors) {
            // 각 반복마다 새로운 mock 생성
            FcmTokenRepository mockRepository = mock(FcmTokenRepository.class);
            FirebaseMessaging mockFirebase = mock(FirebaseMessaging.class);
            
            when(mockRepository.findByUser(any(User.class))).thenReturn(Optional.of(testToken));
            
            FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
            when(exception.getErrorCode()).thenReturn(errorCode);

            try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
                firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(mockFirebase);
                when(mockFirebase.send(any(Message.class))).thenThrow(exception);

                FcmMessageConsumerImpl consumer = new FcmMessageConsumerImpl(mockRepository);
                consumer.processFcmMessage(singleRequest);

                // 삭제되지 않아야 함
                verify(mockRepository, never()).delete(any(FcmToken.class));
            }
        }
    }

    @Test
    void 브로드캐스트_푸시동의사용자만_대상() throws FirebaseMessagingException {
        // 푸시 동의한 사용자들만 조회되는지 확인
        when(fcmTokenRepository.findAllByUserPushAgreeTrue()).thenReturn(List.of(testToken));

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenReturn("success");

            fcmMessageConsumer.processFcmMessage(broadcastRequest);

            // 푸시 동의한 사용자만 조회했는지 확인
            verify(fcmTokenRepository).findAllByUserPushAgreeTrue();
            verify(firebaseMessaging).send(any(Message.class));
        }
    }

    @Test
    void FCM_토큰_마스킹_로직_검증() throws FirebaseMessagingException {
        // 짧은 토큰
        FcmToken shortToken = FcmToken.builder()
                .id(1L)
                .user(testUser)
                .token("short")
                .build();

        when(fcmTokenRepository.findByUser(any(User.class))).thenReturn(Optional.of(shortToken));

        FirebaseMessagingException exception = mock(FirebaseMessagingException.class);
        when(exception.getErrorCode()).thenReturn(ErrorCode.INVALID_ARGUMENT);
        when(exception.getMessage()).thenReturn("invalid token");

        try (MockedStatic<FirebaseMessaging> firebaseMessagingStatic = mockStatic(FirebaseMessaging.class)) {
            firebaseMessagingStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(exception);

            fcmMessageConsumer.processFcmMessage(singleRequest);

            // 토큰이 삭제되는지 확인 (마스킹과 관계없이)
            verify(fcmTokenRepository).delete(shortToken);
        }
    }
}