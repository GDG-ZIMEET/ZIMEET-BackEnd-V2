package com.gdg.z_meet.domain.order.ledger;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentProjectionOutboxWorker {

    private static final int BATCH_SIZE = 100;

    private final PaymentProjectionOutboxRepository outboxRepository;
    private final PaymentProjectionService projectionService;

    @Scheduled(fixedDelayString = "${zimeet.payment-ledger.projection-delay-ms:1000}")
    @Transactional
    public void processPending() {
        List<PaymentProjectionOutbox> batch = outboxRepository.findPendingForUpdate(BATCH_SIZE);
        for (PaymentProjectionOutbox outbox : batch) {
            try {
                projectionService.applyLedger(outbox.getLedgerId());
                outbox.markProcessed();
            } catch (Exception e) {
                log.error("Payment projection update failed - outboxId={}, ledgerId={}: {}",
                        outbox.getOutboxId(), outbox.getLedgerId(), e.getMessage(), e);
                outbox.markFailed(e.getMessage());
            }
        }
    }
}
