package com.gdg.z_meet.domain.fcm.unit.service.chat;

import com.gdg.z_meet.domain.fcm.service.core.FcmMessageServiceImpl;
import com.gdg.z_meet.domain.fcm.service.producer.FcmMessageProducer;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class FcmChatMessageServiceImplTest {

    @Mock
    private UserRepository userRepository;

    @Mock
    private FcmMessageProducer fcmMessageProducer;

    @InjectMocks
    private FcmMessageServiceImpl fcmMessageService;

    @Test
    void 모든_사용자에게_브로드캐스트_메시지_전송() {
        // Given
        String title = "브로드캐스트 제목";
        String body = "브로드캐스트 내용";

        // When
        fcmMessageService.broadcastToAllUsers(title, body);

        // Then
        verify(fcmMessageProducer).sendBroadcastMessage(title, body);
    }

    @Test
    void FCM_서비스_테스트_정상_사용자() {
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
    void FCM_서비스_테스트_존재하지_않는_사용자() {
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
    void 빈_문자열로_브로드캐스트() {
        // Given
        String title = "";
        String body = "";

        // When
        fcmMessageService.broadcastToAllUsers(title, body);

        // Then
        verify(fcmMessageProducer).sendBroadcastMessage(title, body);
    }

    @Test
    void null_문자열로_브로드캐스트() {
        // Given
        String title = null;
        String body = null;

        // When
        fcmMessageService.broadcastToAllUsers(title, body);

        // Then
        verify(fcmMessageProducer).sendBroadcastMessage(title, body);
    }

    @Test
    void null_FCM_토큰으로_테스트() {
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
    void 빈_FCM_토큰으로_테스트() {
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

}
