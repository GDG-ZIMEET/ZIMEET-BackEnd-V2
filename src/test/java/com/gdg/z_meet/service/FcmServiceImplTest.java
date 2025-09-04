package com.gdg.z_meet.service;

import com.gdg.z_meet.domain.fcm.entity.FcmToken;
import com.gdg.z_meet.domain.fcm.repository.FcmTokenRepository;
import com.gdg.z_meet.domain.fcm.service.FcmMessageClient;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import com.google.firebase.messaging.FirebaseMessaging;
import com.google.firebase.messaging.Message;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.MockedStatic;
import org.mockito.Mockito;
import org.mockito.junit.jupiter.MockitoExtension;
import static org.mockito.Mockito.when;
import static org.mockito.Mockito.verify;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.times;

import org.junit.jupiter.api.Test;

import java.util.List;
import java.util.Optional;

@ExtendWith(MockitoExtension.class)
class FcmServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @InjectMocks
    private FcmMessageClient fcmMessageClient;

    @Mock
    private FirebaseMessaging firebaseMessaging;

    @Test
    void sendFcmMessage_유효한_토큰이면_성공적으로_전송됨() throws Exception {
        // given
        Long userId = 1L;
        String token = "유효한 토큰";
        String title = "알림 제목";
        String body = "알림 내용";

        User user = User.builder().id(userId).build();
        FcmToken deviceToken = FcmToken.builder().token(token).user(user).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fcmTokenRepository.findAllByUser(user)).thenReturn(List.of(deviceToken));

        // FirebaseMessaging.getInstance() mocking
        try (MockedStatic<FirebaseMessaging> firebaseStatic = Mockito.mockStatic(FirebaseMessaging.class)) {
            firebaseStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenReturn("알림 전송에 성공하셨습니다 !!!");

            // when
            fcmMessageClient.sendFcmMessage(userId, title, body);

            // then
            verify(firebaseMessaging, times(1)).send(any(Message.class));  // 한 번만 호출되었는지 확인
        }
    }

    @Test
    void sendFcmMessage_유효하지_않은_토큰이면_삭제됨() throws Exception {
        // given
        Long userId = 1L;
        String token = "invalid-token";
        String title = "제목";
        String body = "내용";

        User user = User.builder().id(userId).build();
        FcmToken deviceToken = FcmToken.builder().token(token).user(user).build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));
        when(fcmTokenRepository.findAllByUser(user)).thenReturn(List.of(deviceToken));

        // FirebaseMessaging.getInstance() 모킹
        try (MockedStatic<FirebaseMessaging> firebaseStatic = Mockito.mockStatic(FirebaseMessaging.class)) {
            firebaseStatic.when(FirebaseMessaging::getInstance).thenReturn(firebaseMessaging);
            when(firebaseMessaging.send(any(Message.class))).thenThrow(new BusinessException(Code.FCM_SEND_MESSAGE_ERROR));

            // when
            fcmMessageClient.sendFcmMessage(userId, title, body);

            // then
            verify(fcmTokenRepository).delete(deviceToken);  // 무효 토큰 삭제 확인
        }
    }
}
