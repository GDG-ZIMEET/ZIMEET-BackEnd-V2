package com.gdg.z_meet.domain.order.ledger;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentProjectionOutboxWorkerTest {

    @Mock PaymentProjectionOutboxRepository outboxRepository;
    @Mock PaymentProjectionService projectionService;

    private PaymentProjectionOutboxWorker worker;

    @BeforeEach
    void setUp() {
        worker = new PaymentProjectionOutboxWorker(outboxRepository, projectionService);
    }

    @Test
    void projection_적용에_성공하면_outbox를_PROCESSED로_표시한다() {
        PaymentProjectionOutbox outbox = PaymentProjectionOutbox.builder()
                .ledgerId(1L)
                .orderId("order-1")
                .build();
        when(outboxRepository.findPendingForUpdate(100)).thenReturn(List.of(outbox));

        worker.processPending();

        assertThat(outbox.getStatus()).isEqualTo(ProjectionOutboxStatus.PROCESSED);
        assertThat(outbox.getProcessedAt()).isNotNull();
        assertThat(outbox.getLastError()).isNull();
        verify(projectionService).applyLedger(1L);
    }

    @Test
    void projection_적용에_실패하면_outbox를_FAILED로_표시하고_에러를_남긴다() {
        PaymentProjectionOutbox outbox = PaymentProjectionOutbox.builder()
                .ledgerId(1L)
                .orderId("order-1")
                .build();
        when(outboxRepository.findPendingForUpdate(100)).thenReturn(List.of(outbox));
        doThrow(new IllegalStateException("projection failed")).when(projectionService).applyLedger(1L);

        worker.processPending();

        assertThat(outbox.getStatus()).isEqualTo(ProjectionOutboxStatus.FAILED);
        assertThat(outbox.getRetryCount()).isEqualTo(1);
        assertThat(outbox.getLastError()).isEqualTo("projection failed");
    }
}
