package com.gdg.z_meet.domain.fcm.service.token;

import com.gdg.z_meet.domain.fcm.entity.FcmToken;
import com.gdg.z_meet.domain.fcm.repository.FcmTokenRepository;
import com.gdg.z_meet.domain.user.dto.UserReq;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.argThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FcmTokenServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FcmTokenRepository fcmTokenRepository;

    @InjectMocks
    private FcmTokenServiceImpl fcmTokenService;

    private User testUser;
    private User noPushUser;
    private FcmToken existingToken;

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

        noPushUser = User.builder()
                .id(2L)
                .studentNumber("87654321")
                .name("푸시거부사용자")
                .phoneNumber("010-8765-4321")
                .password("password")
                .pushAgree(false)
                .build();

        existingToken = FcmToken.builder()
                .id(1L)
                .user(testUser)
                .token("existing-token")
                .build();
    }

    @Test
    void 푸시알림_동의성공_true() {
        UserReq.pushAgreeReq req = UserReq.pushAgreeReq.builder()
                .pushAgree(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        boolean result = fcmTokenService.agreePush(1L, req);

        assertTrue(result);
        verify(userRepository).findById(1L);
    }

    @Test
    void 푸시알림_동의성공_false() {
        UserReq.pushAgreeReq req = UserReq.pushAgreeReq.builder()
                .pushAgree(false)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        boolean result = fcmTokenService.agreePush(1L, req);

        assertFalse(result);
        verify(userRepository).findById(1L);
    }

    @Test
    void 푸시알림_동의실패_사용자없음() {
        UserReq.pushAgreeReq req = UserReq.pushAgreeReq.builder()
                .pushAgree(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fcmTokenService.agreePush(1L, req));

        assertEquals(Code.USER_NOT_FOUND, exception.getCode());
    }

    @Test
    void FCM토큰_동기화성공_새토큰생성() {
        UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                .fcmToken("new-fcm-token")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fcmTokenRepository.findByUser(testUser)).thenReturn(Optional.empty());

        fcmTokenService.syncFcmToken(1L, req);

        verify(userRepository).findById(1L);
        verify(fcmTokenRepository).findByUser(testUser);
        verify(fcmTokenRepository).save(argThat(token ->
                token.getToken().equals("new-fcm-token") &&
                        token.getUser().equals(testUser)
        ));
    }

    @Test
    void FCM토큰_동기화성공_기존토큰업데이트() {
        UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                .fcmToken("updated-fcm-token")
                .build();

        FcmToken spyToken = spy(existingToken);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fcmTokenRepository.findByUser(testUser)).thenReturn(Optional.of(spyToken));

        fcmTokenService.syncFcmToken(1L, req);

        verify(userRepository).findById(1L);
        verify(fcmTokenRepository).findByUser(testUser);
        verify(spyToken).setToken("updated-fcm-token");
        verify(fcmTokenRepository, never()).save(any());
    }

    @Test
    void FCM토큰_동기화성공_동일토큰() {
        UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                .fcmToken("existing-token")
                .build();

        FcmToken spyToken = spy(existingToken);

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fcmTokenRepository.findByUser(testUser)).thenReturn(Optional.of(spyToken));

        fcmTokenService.syncFcmToken(1L, req);

        verify(userRepository).findById(1L);
        verify(fcmTokenRepository).findByUser(testUser);
        verify(spyToken, never()).setToken(any());
        verify(fcmTokenRepository, never()).save(any());
    }

    @Test
    void FCM토큰_동기화실패_사용자없음() {
        UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                .fcmToken("new-token")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fcmTokenService.syncFcmToken(1L, req));

        assertEquals(Code.USER_NOT_FOUND, exception.getCode());
    }

    @Test
    void FCM토큰_동기화실패_푸시미동의() {
        UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                .fcmToken("new-token")
                .build();

        when(userRepository.findById(2L)).thenReturn(Optional.of(noPushUser));

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fcmTokenService.syncFcmToken(2L, req));

        assertEquals(Code.FCM_PUSH_NOT_AGREED, exception.getCode());
        verify(fcmTokenRepository, never()).findByUser(any());
        verify(fcmTokenRepository, never()).save(any());
    }

    @Test
    void FCM토큰_동기화_null토큰() {
        UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                .fcmToken(null)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fcmTokenRepository.findByUser(testUser)).thenReturn(Optional.empty());

        fcmTokenService.syncFcmToken(1L, req);

        verify(fcmTokenRepository).save(argThat(token ->
                token.getToken() == null &&
                        token.getUser().equals(testUser)
        ));
    }

    @Test
    void FCM토큰_동기화_빈문자열토큰() {
        UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                .fcmToken("")
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));
        when(fcmTokenRepository.findByUser(testUser)).thenReturn(Optional.empty());

        fcmTokenService.syncFcmToken(1L, req);

        verify(fcmTokenRepository).save(argThat(token ->
                token.getToken().equals("") &&
                        token.getUser().equals(testUser)
        ));
    }
}
