package com.gdg.z_meet.domain.fcm.unit.service.core;

import com.gdg.z_meet.domain.fcm.service.core.FcmMessageServiceImpl;
import com.gdg.z_meet.domain.fcm.service.producer.FcmMessageProducer;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
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
@DisplayName("FcmMessageService 단위 테스트")
class FcmMessageServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FcmMessageProducer fcmMessageProducer;

    @InjectMocks
    private FcmMessageServiceImpl fcmMessageService;

    @Test
    @DisplayName("모든 사용자에게 브로드캐스트 메시지 전송")
    void broadcastToAllUsers() {
        // Given
        String title = "브로드캐스트 제목";
        String body = "브로드캐스트 내용";

        // When
        fcmMessageService.broadcastToAllUsers(title, body);

        // Then
        verify(fcmMessageProducer).sendBroadcastMessage(title, body);
    }

    @Test
    @DisplayName("FCM 서비스 테스트 - 정상 사용자")
    void testFcmService() {
        // Given
        Long userId = 1L;
        String fcmToken = "test-fcm-token";
        User user = User.builder()
                .id(userId)
                .studentNumber("20240001")
                .name("테스트유저")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        fcmMessageService.testFcmService(userId, fcmToken);

        // Then
        verify(userRepository).findById(userId);
        verify(fcmMessageProducer).sendTestMessage(
                eq(userId), 
                eq(fcmToken), 
                eq("ZI-MEET FCM 알림 테스트입니다."), 
                eq("테스트 성공했나요?")
        );
    }

    @Test
    @DisplayName("FCM 서비스 테스트 - 존재하지 않는 사용자")
    void testFcmServiceWithNonExistentUser() {
        // Given
        Long userId = 999L;
        String fcmToken = "test-fcm-token";

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, 
                () -> fcmMessageService.testFcmService(userId, fcmToken));

        assertEquals(Code.USER_NOT_FOUND, exception.getCode());
        verify(userRepository).findById(userId);
        verify(fcmMessageProducer, never()).sendTestMessage(any(), any(), any(), any());
    }

    @Test
    @DisplayName("빈 문자열로 브로드캐스트")
    void broadcastWithEmptyStrings() {
        // Given
        String title = "";
        String body = "";

        // When
        fcmMessageService.broadcastToAllUsers(title, body);

        // Then
        verify(fcmMessageProducer).sendBroadcastMessage(title, body);
    }

    @Test
    @DisplayName("null 문자열로 브로드캐스트")
    void broadcastWithNullStrings() {
        // Given
        String title = null;
        String body = null;

        // When
        fcmMessageService.broadcastToAllUsers(title, body);

        // Then
        verify(fcmMessageProducer).sendBroadcastMessage(title, body);
    }

    @Test
    @DisplayName("null FCM 토큰으로 테스트")
    void testWithNullFcmToken() {
        // Given
        Long userId = 1L;
        String fcmToken = null;
        User user = User.builder()
                .id(userId)
                .studentNumber("20240001")
                .name("테스트유저")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        fcmMessageService.testFcmService(userId, fcmToken);

        // Then
        verify(fcmMessageProducer).sendTestMessage(
                eq(userId), 
                eq(fcmToken), 
                eq("ZI-MEET FCM 알림 테스트입니다."), 
                eq("테스트 성공했나요?")
        );
    }

    @Test
    @DisplayName("빈 FCM 토큰으로 테스트")
    void testWithEmptyFcmToken() {
        // Given
        Long userId = 1L;
        String fcmToken = "";
        User user = User.builder()
                .id(userId)
                .studentNumber("20240001")
                .name("테스트유저")
                .build();

        when(userRepository.findById(userId)).thenReturn(Optional.of(user));

        // When
        fcmMessageService.testFcmService(userId, fcmToken);

        // Then
        verify(fcmMessageProducer).sendTestMessage(
                eq(userId), 
                eq(fcmToken), 
                eq("ZI-MEET FCM 알림 테스트입니다."), 
                eq("테스트 성공했나요?")
        );
    }

    @Test
    @DisplayName("null userId로 테스트")
    void testWithNullUserId() {
        // Given
        Long userId = null;
        String fcmToken = "test-token";

        when(userRepository.findById(userId)).thenReturn(Optional.empty());

        // When & Then
        BusinessException exception = assertThrows(BusinessException.class, 
                () -> fcmMessageService.testFcmService(userId, fcmToken));

        assertEquals(Code.USER_NOT_FOUND, exception.getCode());
    }
}
