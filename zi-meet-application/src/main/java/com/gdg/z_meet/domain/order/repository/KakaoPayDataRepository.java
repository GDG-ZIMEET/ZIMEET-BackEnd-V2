package com.gdg.z_meet.domain.order.repository;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KakaoPayDataRepository extends JpaRepository<KakaoPayData, Long> {
        Optional<KakaoPayData> findByOrderId(String orderId);

        Optional<KakaoPayData> findByTid(String tid);

        @Query(value = "SELECT * FROM kakao_pay_data k " +
                        "WHERE k.outbox_status = :outboxStatus AND k.status = :status " +
                        "FOR UPDATE SKIP LOCKED", nativeQuery = true)
        List<KakaoPayData> findByOutboxStatusAndStatus(
                        @Param("outboxStatus") String outboxStatus,
                        @Param("status") String status);

        List<KakaoPayData> findByStatusAndCreatedAtBefore(
                        com.gdg.z_meet.domain.order.entity.enums.PaymentStatus status,
                        java.time.LocalDateTime createdAt);

        @Query(value = "SELECT * FROM kakao_pay_data k " +
                        "WHERE k.recovery_status = 'PENDING' " +
                        "AND (k.next_recovery_at IS NULL OR k.next_recovery_at <= :now) " +
                        "FOR UPDATE SKIP LOCKED", nativeQuery = true)
        List<KakaoPayData> findRecoveryTasksToProcess(
                        @Param("now") java.time.LocalDateTime now,
                        org.springframework.data.domain.Pageable pageable);

        List<KakaoPayData> findByStatusAndIsSettledFalse(com.gdg.z_meet.domain.order.entity.enums.PaymentStatus status);

        List<KakaoPayData> findBySettlementId(Long settlementId);
}
