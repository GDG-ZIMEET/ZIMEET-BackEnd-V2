package com.gdg.z_meet.domain.order.ledger;

import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import org.springframework.stereotype.Component;

import java.util.EnumMap;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;

@Component
public class PaymentLedgerTransitionValidator {

    private final Map<PaymentStatus, Set<PaymentStatus>> allowedTransitions = new EnumMap<>(PaymentStatus.class);

    public PaymentLedgerTransitionValidator() {
        allowedTransitions.put(PaymentStatus.PREPARED,
                EnumSet.of(PaymentStatus.PROCESSING, PaymentStatus.CANCELLED, PaymentStatus.FAILED));
        allowedTransitions.put(PaymentStatus.PROCESSING,
                EnumSet.of(PaymentStatus.APPROVED, PaymentStatus.UNKNOWN, PaymentStatus.FAILED, PaymentStatus.CANCELLED));
        allowedTransitions.put(PaymentStatus.UNKNOWN,
                EnumSet.of(PaymentStatus.APPROVED, PaymentStatus.FAILED, PaymentStatus.CANCELLED));
        allowedTransitions.put(PaymentStatus.APPROVED,
                EnumSet.of(PaymentStatus.CANCELLED));
        allowedTransitions.put(PaymentStatus.CANCELLED, EnumSet.noneOf(PaymentStatus.class));
        allowedTransitions.put(PaymentStatus.FAILED, EnumSet.noneOf(PaymentStatus.class));
    }

    public void validate(PaymentStatus currentStatus, PaymentStatus nextStatus, PaymentLedgerEventType eventType) {
        if (eventType == PaymentLedgerEventType.LEGACY_IMPORTED) {
            return;
        }
        if (currentStatus == null) {
            if (nextStatus != PaymentStatus.PREPARED && nextStatus != PaymentStatus.PROCESSING) {
                throw new IllegalStateException("Initial payment state must start from PREPARED or PROCESSING: " + nextStatus);
            }
            return;
        }
        if (currentStatus == nextStatus) {
            return;
        }
        if (!allowedTransitions.getOrDefault(currentStatus, Set.of()).contains(nextStatus)) {
            throw new IllegalStateException("Illegal payment transition: " + currentStatus + " -> " + nextStatus);
        }
    }
}
