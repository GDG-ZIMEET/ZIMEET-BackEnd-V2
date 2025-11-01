package com.gdg.z_meet.domain.fcm.unit.service.token;

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
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmTokenService 단위 테스트")
class FcmTokenServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private com.gdg.z_meet.domain.fcm.service.token.FcmTokenTransactionService fcmTokenTransactionService;

    @InjectMocks
    private FcmTokenServiceImpl fcmTokenService;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .id(1L)
                .pushAgree(true)
                .build();
    }

    @Test
    @DisplayName("푸시 알림 동의 성공")
    void 푸시알림_동의성공() {
        UserReq.pushAgreeReq req = UserReq.pushAgreeReq.builder()
                .pushAgree(true)
                .build();

        when(userRepository.findById(1L)).thenReturn(Optional.of(testUser));

        boolean result = fcmTokenService.agreePush(1L, req);

        assertTrue(result);
        verify(userRepository).findById(1L);
    }

    @Test
    @DisplayName("푸시 알림 동의 실패 - 사용자 없음")
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
    @DisplayName("FCM 토큰 동기화 성공")
    void FCM토큰_동기화성공() {
        UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                .fcmToken("new-fcm-token")
                .build();

        doNothing().when(fcmTokenTransactionService).doSyncFcmToken(1L, req);

        fcmTokenService.syncFcmToken(1L, req);

        verify(fcmTokenTransactionService).doSyncFcmToken(1L, req);
    }

    @Test
    @DisplayName("FCM 토큰 동기화 실패 - 사용자 없음")
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
    @DisplayName("FCM 토큰 동기화 실패 - 푸시 미동의")
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
}
