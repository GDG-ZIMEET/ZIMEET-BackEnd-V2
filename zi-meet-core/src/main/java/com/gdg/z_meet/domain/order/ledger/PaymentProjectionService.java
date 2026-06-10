package com.gdg.z_meet.domain.order.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentProjectionService {

    private final PaymentLedgerRepository ledgerRepository;
    private final PaymentCurrentProjectionRepository projectionRepository;

    @Transactional
    public void applyLedger(Long ledgerId) {
        PaymentLedgerEntry entry = ledgerRepository.findById(ledgerId)
                .orElseThrow(() -> new IllegalArgumentException("Payment ledger not found: " + ledgerId));

        PaymentCurrentProjection projection = projectionRepository.findById(entry.getOrderId())
                .orElseGet(() -> new PaymentCurrentProjection(entry.getOrderId()));
        projection.apply(entry);
        projectionRepository.save(projection);
    }
}
