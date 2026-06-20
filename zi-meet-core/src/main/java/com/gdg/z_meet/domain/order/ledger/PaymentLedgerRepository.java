package com.gdg.z_meet.domain.order.ledger;

import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface PaymentLedgerRepository extends JpaRepository<PaymentLedgerEntry, Long> {

    Optional<PaymentLedgerEntry> findTopByOrderIdOrderByLedgerIdDesc(String orderId);

    Optional<PaymentLedgerEntry> findByRequestId(String requestId);

    boolean existsBySourcePaymentIdAndEventType(Long sourcePaymentId, PaymentLedgerEventType eventType);

    List<PaymentLedgerEntry> findByOrderIdOrderByLedgerIdAsc(String orderId);

    List<PaymentLedgerEntry> findBySourcePaymentIdOrderByLedgerIdAsc(Long sourcePaymentId);

    List<PaymentLedgerEntry> findAllByOrderByLedgerIdAsc();

    long countByEventType(PaymentLedgerEventType eventType);
}
