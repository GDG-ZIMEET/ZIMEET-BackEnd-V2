package com.gdg.z_meet.domain.order.ledger;

import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.entity.enums.ProductType;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.transaction.annotation.Isolation;
import org.springframework.transaction.annotation.Transactional;

import java.lang.reflect.Method;
import java.time.LocalDateTime;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class PaymentLedgerAppendServiceTest {

    @Mock PaymentLedgerRepository ledgerRepository;
    @Mock PaymentProjectionOutboxRepository outboxRepository;
    @Mock PaymentLedgerTransitionValidator transitionValidator;

    private PaymentLedgerAppendService service;

    @BeforeEach
    void setUp() {
        service = new PaymentLedgerAppendService(ledgerRepository, outboxRepository, transitionValidator);
    }

    @Test
    void append는_주문별_상태전이_직렬화를_위해_SERIALIZABLE로_실행된다() throws Exception {
        Method append = PaymentLedgerAppendService.class.getMethod("append", PaymentLedgerAppendCommand.class);

        Transactional transactional = append.getAnnotation(Transactional.class);

        assertThat(transactional).isNotNull();
        assertThat(transactional.isolation()).isEqualTo(Isolation.SERIALIZABLE);
    }

    @Test
    void 같은_requestId가_이미_있으면_원장과_projection_outbox를_다시_쓰지_않는다() {
        PaymentLedgerEntry existing = entry(PaymentStatus.PROCESSING, "req-1");
        when(ledgerRepository.findByRequestId("req-1")).thenReturn(Optional.of(existing));

        PaymentLedgerEntry result = service.append(command(PaymentStatus.PROCESSING, "req-1"));

        assertThat(result).isSameAs(existing);
        verify(ledgerRepository, never()).save(any());
        verify(outboxRepository, never()).save(any());
    }

    @Test
    void 최신_원장을_잠그고_다음_상태전이를_검증한_뒤_projection_outbox를_남긴다() {
        PaymentLedgerEntry latest = entry(PaymentStatus.PROCESSING, "req-prev");
        PaymentLedgerEntry saved = entry(PaymentStatus.APPROVED, "req-approved");
        when(ledgerRepository.findByRequestId("req-approved")).thenReturn(Optional.empty());
        when(ledgerRepository.findFirstByOrderIdOrderByLedgerIdDesc("order-1")).thenReturn(Optional.of(latest));
        when(ledgerRepository.save(any(PaymentLedgerEntry.class))).thenReturn(saved);
        when(outboxRepository.save(any(PaymentProjectionOutbox.class))).thenAnswer(invocation -> invocation.getArgument(0));

        PaymentLedgerEntry result = service.append(command(PaymentStatus.APPROVED, "req-approved"));

        assertThat(result).isSameAs(saved);
        verify(transitionValidator).validate(
                PaymentStatus.PROCESSING,
                PaymentStatus.APPROVED,
                PaymentLedgerEventType.PAYMENT_APPROVED);
        verify(outboxRepository).save(argThat(outbox -> "order-1".equals(outbox.getOrderId())));
    }

    private PaymentLedgerAppendCommand command(PaymentStatus nextStatus, String requestId) {
        return new PaymentLedgerAppendCommand(
                1L,
                "order-1",
                "tid-1",
                PaymentLedgerEventType.PAYMENT_APPROVED,
                nextStatus,
                ProductType.TICKET,
                1200L,
                10L,
                null,
                PaymentLedgerActorType.SYSTEM,
                "test",
                "reason",
                requestId,
                null,
                "metadata",
                LocalDateTime.now()
        );
    }

    private PaymentLedgerEntry entry(PaymentStatus nextStatus, String requestId) {
        return PaymentLedgerEntry.builder()
                .orderId("order-1")
                .pgTid("tid-1")
                .eventType(PaymentLedgerEventType.PAYMENT_APPROVED)
                .nextStatus(nextStatus)
                .productType(ProductType.TICKET)
                .amount(1200L)
                .buyerId(10L)
                .actorType(PaymentLedgerActorType.SYSTEM)
                .actorId("test")
                .reason("reason")
                .requestId(requestId)
                .occurredAt(LocalDateTime.now())
                .build();
    }
}
