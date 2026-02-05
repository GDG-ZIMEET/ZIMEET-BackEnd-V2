package com.gdg.z_meet.domain.order.dto;

import lombok.Builder;
import lombok.Getter;

import java.time.LocalDateTime;

/**
 * 결제 상태 알림 DTO
 * WebSocket 또는 FCM을 통해 사용자에게 비동기 결제 결과를 전달하기 위한 DTO
 */
@Getter
@Builder
public class PaymentNotificationDTO {

    private String orderId;
    private String status; // PROCESSING, APPROVED, FAILED, CANCELLED
    private String message;
    private LocalDateTime timestamp;

    public static PaymentNotificationDTO processing(String orderId) {
        return PaymentNotificationDTO.builder()
                .orderId(orderId)
                .status("PROCESSING")
                .message("결제 처리 중입니다. 잠시만 기다려주세요.")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static PaymentNotificationDTO approved(String orderId) {
        return PaymentNotificationDTO.builder()
                .orderId(orderId)
                .status("APPROVED")
                .message("결제가 완료되었습니다.")
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static PaymentNotificationDTO failed(String orderId, String reason) {
        return PaymentNotificationDTO.builder()
                .orderId(orderId)
                .status("FAILED")
                .message("결제에 실패했습니다: " + reason)
                .timestamp(LocalDateTime.now())
                .build();
    }

    public static PaymentNotificationDTO cancelled(String orderId) {
        return PaymentNotificationDTO.builder()
                .orderId(orderId)
                .status("CANCELLED")
                .message("결제가 취소되었습니다.")
                .timestamp(LocalDateTime.now())
                .build();
    }
}
