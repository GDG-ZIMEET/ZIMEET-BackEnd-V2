package com.gdg.z_meet.domain.order.ledger;

import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface PaymentCurrentProjectionRepository extends JpaRepository<PaymentCurrentProjection, String> {

    long countByCurrentStatus(PaymentStatus status);

    Optional<PaymentCurrentProjection> findBySourcePaymentId(Long sourcePaymentId);

    Optional<PaymentCurrentProjection> findTopByOrderByLatestLedgerIdDesc();

    @Query("""
           SELECT COALESCE(SUM(p.amount), 0)
           FROM PaymentCurrentProjection p
           WHERE p.currentStatus = :status
           AND p.settled = false
           AND (:clubId IS NULL OR p.clubId = :clubId)
           """)
    long sumUnsettledAmount(@Param("status") PaymentStatus status, @Param("clubId") Long clubId);
}
