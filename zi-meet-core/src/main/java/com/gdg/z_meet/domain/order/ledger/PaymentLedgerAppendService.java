package com.gdg.z_meet.domain.order.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentLedgerAppendService {

    private final PaymentLedgerRepository ledgerRepository;
    private final PaymentProjectionOutboxRepository outboxRepository;
    private final PaymentLedgerTransitionValidator transitionValidator;

    @Transactional
    public PaymentLedgerEntry append(PaymentLedgerAppendCommand command) {
        var latest = ledgerRepository.findTopByOrderIdOrderByLedgerIdDesc(command.orderId()).orElse(null);
        var previousStatus = latest != null ? latest.getNextStatus() : null;

        transitionValidator.validate(previousStatus, command.nextStatus(), command.eventType());

        PaymentLedgerEntry entry = PaymentLedgerEntry.builder()
                .sourcePaymentId(command.sourcePaymentId())
                .orderId(command.orderId())
                .pgTid(command.pgTid())
                .eventType(command.eventType())
                .previousStatus(previousStatus)
                .nextStatus(command.nextStatus())
                .productType(command.productType())
                .amount(command.amount())
                .buyerId(command.buyerId())
                .clubId(command.clubId())
                .actorType(command.actorType())
                .actorId(command.actorId())
                .reason(command.reason())
                .requestId(command.requestId())
                .idempotencyKey(command.idempotencyKey())
                .metadata(command.metadata())
                .occurredAt(command.occurredAt())
                .build();
        PaymentLedgerEntry saved = ledgerRepository.save(entry);

        outboxRepository.save(PaymentProjectionOutbox.builder()
                .ledgerId(saved.getLedgerId())
                .orderId(saved.getOrderId())
                .build());

        return saved;
    }
}
