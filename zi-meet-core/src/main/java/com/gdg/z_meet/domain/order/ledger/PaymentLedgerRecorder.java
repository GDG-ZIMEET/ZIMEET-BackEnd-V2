package com.gdg.z_meet.domain.order.ledger;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Component
@RequiredArgsConstructor
public class PaymentLedgerRecorder {

    private final PaymentLedgerAppendService appendService;

    public void record(
            KakaoPayData payment,
            PaymentStatus previousStatus,
            PaymentStatus nextStatus,
            PaymentLedgerActorType actorType,
            String actorId,
            String reason,
            String requestId,
            String metadata
    ) {
        appendService.append(new PaymentLedgerAppendCommand(
                payment.getId(),
                payment.getOrderId(),
                payment.getTid(),
                resolveEventType(previousStatus, nextStatus),
                nextStatus,
                payment.getProductType(),
                payment.getTotalPrice(),
                payment.getBuyer() != null ? payment.getBuyer().getId() : null,
                payment.getClub() != null ? payment.getClub().getId() : null,
                actorType,
                actorId,
                reason,
                requestId,
                null,
                metadata,
                LocalDateTime.now()
        ));
    }

    private PaymentLedgerEventType resolveEventType(PaymentStatus previousStatus, PaymentStatus nextStatus) {
        if (nextStatus == PaymentStatus.PREPARED) {
            return PaymentLedgerEventType.PAYMENT_PREPARED;
        }
        if (nextStatus == PaymentStatus.PROCESSING) {
            return PaymentLedgerEventType.PAYMENT_PROCESSING_STARTED;
        }
        if (nextStatus == PaymentStatus.APPROVED) {
            return previousStatus == PaymentStatus.UNKNOWN
                    ? PaymentLedgerEventType.PAYMENT_RECOVERED
                    : PaymentLedgerEventType.PAYMENT_APPROVED;
        }
        if (nextStatus == PaymentStatus.UNKNOWN) {
            return PaymentLedgerEventType.PAYMENT_UNKNOWN;
        }
        if (nextStatus == PaymentStatus.CANCELLED) {
            return PaymentLedgerEventType.PAYMENT_CANCELLED;
        }
        if (nextStatus == PaymentStatus.FAILED) {
            return PaymentLedgerEventType.PAYMENT_FAILED;
        }
        throw new IllegalArgumentException("Unsupported payment status for ledger: " + nextStatus);
    }
}
