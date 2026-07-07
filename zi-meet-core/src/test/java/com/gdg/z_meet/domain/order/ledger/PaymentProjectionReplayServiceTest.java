package com.gdg.z_meet.domain.order.ledger;

import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.entity.enums.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.test.util.ReflectionTestUtils;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentProjectionReplayServiceTest {

    @Mock PaymentLedgerRepository ledgerRepository;
    @Mock PaymentCurrentProjectionRepository projectionRepository;

    private PaymentProjectionReplayService service;

    @BeforeEach
    void setUp() {
        service = new PaymentProjectionReplayService(ledgerRepository, projectionRepository);
    }

    @Test
    void 원장_전체를_순서대로_재생해_현재_상태_projection을_재구성한다() {
        PaymentLedgerEntry prepared = entry(1L, PaymentStatus.PREPARED, PaymentLedgerEventType.PAYMENT_PREPARED);
        PaymentLedgerEntry processing = entry(2L, PaymentStatus.PROCESSING, PaymentLedgerEventType.PAYMENT_PROCESSING_STARTED);
        PaymentLedgerEntry approved = entry(3L, PaymentStatus.APPROVED, PaymentLedgerEventType.PAYMENT_APPROVED);
        PaymentCurrentProjection projection = new PaymentCurrentProjection("order-1");

        when(ledgerRepository.findAllByOrderByLedgerIdAsc()).thenReturn(List.of(prepared, processing, approved));
        when(projectionRepository.findById("order-1")).thenReturn(
                Optional.empty(),
                Optional.of(projection),
                Optional.of(projection));
        when(projectionRepository.count()).thenReturn(1L);

        PaymentProjectionReplayService.ReplayResult result = service.rebuildCurrentProjection();

        assertThat(result.appliedLedgerCount()).isEqualTo(3L);
        assertThat(result.projectionCount()).isEqualTo(1L);
        assertThat(projection.getCurrentStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(projection.getLatestLedgerId()).isEqualTo(3L);
        assertThat(projection.getLastEventType()).isEqualTo(PaymentLedgerEventType.PAYMENT_APPROVED);
        verify(projectionRepository).deleteAllInBatch();
        verify(projectionRepository, times(3)).save(any(PaymentCurrentProjection.class));
    }

    private PaymentLedgerEntry entry(Long ledgerId, PaymentStatus nextStatus, PaymentLedgerEventType eventType) {
        PaymentLedgerEntry entry = PaymentLedgerEntry.builder()
                .sourcePaymentId(1L)
                .orderId("order-1")
                .pgTid("tid-1")
                .eventType(eventType)
                .nextStatus(nextStatus)
                .productType(ProductType.TICKET)
                .amount(1200L)
                .buyerId(10L)
                .actorType(PaymentLedgerActorType.SYSTEM)
                .actorId("test")
                .reason("reason")
                .requestId("request-" + ledgerId)
                .occurredAt(LocalDateTime.now())
                .build();
        ReflectionTestUtils.setField(entry, "ledgerId", ledgerId);
        return entry;
    }
}
