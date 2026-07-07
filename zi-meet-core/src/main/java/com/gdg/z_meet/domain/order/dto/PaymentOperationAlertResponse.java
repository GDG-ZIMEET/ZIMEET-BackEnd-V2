package com.gdg.z_meet.domain.order.dto;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.KakaoPayData.RecoveryStatus;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.entity.enums.ProductType;

import java.time.LocalDateTime;

public record PaymentOperationAlertResponse(
        Long paymentId,
        String orderId,
        String tid,
        PaymentStatus status,
        ProductType productType,
        Long totalPrice,
        Long buyerId,
        RecoveryStatus recoveryStatus,
        int recoveryRetryCount,
        LocalDateTime nextRecoveryAt,
        String cancelReason,
        LocalDateTime createdAt,
        LocalDateTime updatedAt
) {

    public static PaymentOperationAlertResponse from(KakaoPayData data) {
        Long buyerId = data.getBuyer() != null ? data.getBuyer().getId() : null;
        return new PaymentOperationAlertResponse(
                data.getId(),
                data.getOrderId(),
                data.getTid(),
                data.getStatus(),
                data.getProductType(),
                data.getTotalPrice(),
                buyerId,
                data.getRecoveryStatus(),
                data.getRecoveryRetryCount(),
                data.getNextRecoveryAt(),
                data.getCancelReason(),
                data.getCreatedAt(),
                data.getUpdatedAt()
        );
    }
}
