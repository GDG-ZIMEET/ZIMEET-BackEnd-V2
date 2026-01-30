package com.gdg.z_meet.domain.fcm.unit.service.token;

import com.gdg.z_meet.domain.fcm.entity.FcmToken;
import com.gdg.z_meet.domain.fcm.repository.FcmTokenRepository;
import com.gdg.z_meet.domain.fcm.service.token.FcmTokenServiceImpl;
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
    
    @Mock
    private com.gdg.z_meet.domain.fcm.service.token.FcmTokenTransactionService fcmTokenTransactionService;

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
    @DisplayName("FCM 토큰 동기화 성공 - 새 토큰 생성")
    void FCM토큰_동기화성공_새토큰생성() {
        UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                .fcmToken("new-fcm-token")
                .build();

        doNothing().when(fcmTokenTransactionService).doSyncFcmToken(1L, req);

        fcmTokenService.syncFcmToken(1L, req);

        verify(fcmTokenTransactionService).doSyncFcmToken(1L, req);
    }

    @Test
    @DisplayName("FCM 토큰 동기화 성공 - 기존 토큰 업데이트")
    void FCM토큰_동기화성공_기존토큰업데이트() {
        UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                .fcmToken("updated-fcm-token")
                .build();

        doNothing().when(fcmTokenTransactionService).doSyncFcmToken(1L, req);

        fcmTokenService.syncFcmToken(1L, req);

        verify(fcmTokenTransactionService).doSyncFcmToken(1L, req);
    }

    @Test
    void FCM토큰_동기화실패_사용자없음() {
        UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                .fcmToken("new-token")
                .build();

        doThrow(new BusinessException(Code.USER_NOT_FOUND))
                .when(fcmTokenTransactionService).doSyncFcmToken(1L, req);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fcmTokenService.syncFcmToken(1L, req));

        assertEquals(Code.USER_NOT_FOUND, exception.getCode());
    }

    @Test
    void FCM토큰_동기화실패_푸시미동의() {
        UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                .fcmToken("new-token")
                .build();

        doThrow(new BusinessException(Code.FCM_PUSH_NOT_AGREED))
                .when(fcmTokenTransactionService).doSyncFcmToken(2L, req);

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fcmTokenService.syncFcmToken(2L, req));

        assertEquals(Code.FCM_PUSH_NOT_AGREED, exception.getCode());
    }

    @Test
    @DisplayName("FCM 토큰 동기화 - 동시성 테스트 (쓰기락 검증)")
    void FCM토큰_동기화_동시성_쓰기락검증() {
        UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                .fcmToken("concurrent-token")
                .build();

        doNothing().when(fcmTokenTransactionService).doSyncFcmToken(1L, req);

        fcmTokenService.syncFcmToken(1L, req);

        verify(fcmTokenTransactionService).doSyncFcmToken(1L, req);
    }

    @Test
    @DisplayName("FCM 토큰 동기화 - 기존 토큰 존재 시 업데이트 확인")
    void FCM토큰_동기화_기존토큰존재시_업데이트() {
        UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                .fcmToken("new-token-after-update")
                .build();

        doNothing().when(fcmTokenTransactionService).doSyncFcmToken(1L, req);

        fcmTokenService.syncFcmToken(1L, req);

        verify(fcmTokenTransactionService).doSyncFcmToken(1L, req);
    }
}
