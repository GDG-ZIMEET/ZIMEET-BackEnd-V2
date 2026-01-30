package com.gdg.z_meet.domain.order.repository;

import com.gdg.z_meet.domain.order.entity.PaymentRecovery;
import jakarta.persistence.LockModeType;
import jakarta.persistence.QueryHint;
import org.springframework.data.domain.Pageable;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;

public interface PaymentRecoveryRepository extends JpaRepository<PaymentRecovery, Long> {

    /**
     * 처리할 대상 조회
     * PENDING 상태이면서 재시도 시간이 된 건을 조회
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints({ @QueryHint(name = "javax.persistence.lock.timeout", value = "-2") }) // -2: SKIP LOCKED
    @Query("SELECT pr FROM PaymentRecovery pr " +
            "WHERE pr.status = 'PENDING' " +
            "AND pr.nextRetryAt <= :now " +
            "ORDER BY pr.nextRetryAt ASC")
    List<PaymentRecovery> findTasksToProcess(@Param("now") LocalDateTime now, Pageable pageable);
}