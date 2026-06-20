package com.gdg.z_meet.domain.order.repository;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface KakaoPayDataRepository extends JpaRepository<KakaoPayData, Long> {
        Optional<KakaoPayData> findByOrderId(String orderId);

        Optional<KakaoPayData> findByTid(String tid);

        Optional<KakaoPayData> findByReadyIdempotencyKey(String readyIdempotencyKey);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = """
                        UPDATE kakao_pay_data
                           SET status = 'PROCESSING', pg_token = :pgToken, outbox_status = 'INIT'
                         WHERE order_id = :orderId AND status = 'PREPARED'
                        """, nativeQuery = true)
        int claimApproval(@Param("orderId") String orderId, @Param("pgToken") String pgToken);

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
                        "WHERE k.recovery_status IN ('PENDING', 'PROCESSING') " +
                        "AND (k.next_recovery_at IS NULL OR k.next_recovery_at <= :now) " +
                        "FOR UPDATE SKIP LOCKED", nativeQuery = true)
        List<KakaoPayData> findRecoveryTasksToProcess(
                        @Param("now") java.time.LocalDateTime now,
                        org.springframework.data.domain.Pageable pageable);

        @Modifying(clearAutomatically = true, flushAutomatically = true)
        @Query(value = """
                        UPDATE kakao_pay_data
                           SET recovery_status = 'PROCESSING', next_recovery_at = :leaseExpiresAt
                         WHERE id = :id
                           AND recovery_status IN ('PENDING', 'PROCESSING')
                           AND (next_recovery_at IS NULL OR next_recovery_at <= :now)
                        """, nativeQuery = true)
        int claimRecovery(@Param("id") Long id,
                          @Param("now") java.time.LocalDateTime now,
                          @Param("leaseExpiresAt") java.time.LocalDateTime leaseExpiresAt);

        List<KakaoPayData> findByStatusAndIsSettledFalse(com.gdg.z_meet.domain.order.entity.enums.PaymentStatus status);

        long countByStatus(com.gdg.z_meet.domain.order.entity.enums.PaymentStatus status);

        long countByStatusAndIsSettledFalse(com.gdg.z_meet.domain.order.entity.enums.PaymentStatus status);

        List<KakaoPayData> findByClubIdAndStatusAndIsSettledFalse(Long clubId, com.gdg.z_meet.domain.order.entity.enums.PaymentStatus status);

        long countByClubIdAndStatusAndIsSettledFalse(Long clubId, com.gdg.z_meet.domain.order.entity.enums.PaymentStatus status);

        @Query("SELECT COALESCE(SUM(k.totalPrice), 0) FROM KakaoPayData k WHERE k.club.id = :clubId AND k.status = :status AND k.isSettled = false")
        long sumTotalPriceByClubIdAndStatusAndIsSettledFalse(@Param("clubId") Long clubId, @Param("status") com.gdg.z_meet.domain.order.entity.enums.PaymentStatus status);

        List<KakaoPayData> findBySettlementId(Long settlementId);
}
