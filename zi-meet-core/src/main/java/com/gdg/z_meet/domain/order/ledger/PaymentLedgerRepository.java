package com.gdg.z_meet.domain.order.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;

import java.util.List;
import java.util.Optional;

import jakarta.persistence.LockModeType;

public interface PaymentLedgerRepository extends JpaRepository<PaymentLedgerEntry, Long> {

    Optional<PaymentLedgerEntry> findTopByOrderIdOrderByLedgerIdDesc(String orderId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    Optional<PaymentLedgerEntry> findFirstByOrderIdOrderByLedgerIdDesc(String orderId);

    Optional<PaymentLedgerEntry> findByRequestId(String requestId);

    boolean existsBySourcePaymentIdAndEventType(Long sourcePaymentId, PaymentLedgerEventType eventType);

    List<PaymentLedgerEntry> findByOrderIdOrderByLedgerIdAsc(String orderId);

    List<PaymentLedgerEntry> findBySourcePaymentIdOrderByLedgerIdAsc(Long sourcePaymentId);

    List<PaymentLedgerEntry> findAllByOrderByLedgerIdAsc();

    long countByEventType(PaymentLedgerEventType eventType);
}
