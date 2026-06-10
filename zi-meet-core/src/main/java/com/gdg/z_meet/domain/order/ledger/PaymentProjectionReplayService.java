package com.gdg.z_meet.domain.order.ledger;

import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class PaymentProjectionReplayService {

    private final PaymentLedgerRepository ledgerRepository;
    private final PaymentCurrentProjectionRepository projectionRepository;

    @Transactional
    public ReplayResult rebuildCurrentProjection() {
        projectionRepository.deleteAllInBatch();

        long applied = 0;
        for (PaymentLedgerEntry entry : ledgerRepository.findAllByOrderByLedgerIdAsc()) {
            PaymentCurrentProjection projection = projectionRepository.findById(entry.getOrderId())
                    .orElseGet(() -> new PaymentCurrentProjection(entry.getOrderId()));
            projection.apply(entry);
            projectionRepository.save(projection);
            applied++;
        }
        return new ReplayResult(applied, projectionRepository.count());
    }

    public record ReplayResult(long appliedLedgerCount, long projectionCount) {
    }
}
