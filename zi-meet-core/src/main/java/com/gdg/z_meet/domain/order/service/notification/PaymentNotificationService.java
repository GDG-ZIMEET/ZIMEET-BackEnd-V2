package com.gdg.z_meet.domain.order.service.notification;

import com.gdg.z_meet.domain.order.dto.PaymentNotificationDTO;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 결제 알림 서비스
 * 향후 WebSocket 또는 FCM을 통한 실시간 알림 기능 구현을 위한 인터페이스
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentNotificationService {

    // TODO: WebSocket MessageSendingOperations 또는 FCM Service 주입
    // private final SimpMessagingTemplate messagingTemplate;
    // private final FirebaseMessaging firebaseMessaging;

    /**
     * 사용자에게 결제 상태 알림 전송
     * 현재는 로깅만 수행하며, 향후 WebSocket/FCM 연동 시 실제 알림 전송
     */
    public void notifyPaymentStatus(Long userId, PaymentNotificationDTO notification) {
        log.info("결제 상태 알림 전송 - userId: {}, orderId: {}, status: {}",
                userId, notification.getOrderId(), notification.getStatus());

        // TODO: WebSocket을 통한 실시간 알림
        // messagingTemplate.convertAndSendToUser(
        // userId.toString(),
        // "/queue/payment",
        // notification
        // );

        // TODO: FCM을 통한 푸시 알림 (앱이 백그라운드일 경우)
        // sendFcmNotification(userId, notification);
    }

    /**
     * 결제 처리 시작 알림
     */
    public void notifyProcessing(Long userId, String orderId) {
        notifyPaymentStatus(userId, PaymentNotificationDTO.processing(orderId));
    }

    /**
     * 결제 승인 완료 알림
     */
    public void notifyApproved(Long userId, String orderId) {
        notifyPaymentStatus(userId, PaymentNotificationDTO.approved(orderId));
    }

    /**
     * 결제 실패 알림
     */
    public void notifyFailed(Long userId, String orderId, String reason) {
        notifyPaymentStatus(userId, PaymentNotificationDTO.failed(orderId, reason));
    }

    /**
     * 결제 취소 알림
     */
    public void notifyCancelled(Long userId, String orderId) {
        notifyPaymentStatus(userId, PaymentNotificationDTO.cancelled(orderId));
    }
}
