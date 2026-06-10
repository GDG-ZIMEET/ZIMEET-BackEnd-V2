package com.gdg.z_meet.domain.order.ledger;

import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.entity.enums.ProductType;

import java.time.LocalDateTime;

public record PaymentLedgerAppendCommand(
        Long sourcePaymentId,
        String orderId,
        String pgTid,
        PaymentLedgerEventType eventType,
        PaymentStatus nextStatus,
        ProductType productType,
        Long amount,
        Long buyerId,
        Long clubId,
        PaymentLedgerActorType actorType,
        String actorId,
        String reason,
        String requestId,
        String idempotencyKey,
        String metadata,
        LocalDateTime occurredAt
) {
}
