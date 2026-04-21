package com.gdg.z_meet.payment.outbox;

import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.jpa.repository.QueryHints;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface OutboxRepository extends JpaRepository<OutboxEntry, Long> {

    /**
     * Relay 가 발행 대상 행을 id 오름차순으로 조회.
     * 다중 Relay 인스턴스 환경에서는 SKIP LOCKED 로 중복 선점을 방지한다.
     */
    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @QueryHints(@jakarta.persistence.QueryHint(
            name = "jakarta.persistence.lock.timeout",
            value = "0"
    ))
    @Query("""
           SELECT o FROM OutboxEntry o
           WHERE o.status = com.gdg.z_meet.payment.outbox.OutboxEntry.Status.PENDING
           ORDER BY o.id ASC
           """)
    List<OutboxEntry> lockNextPendingBatch(int limit);

    /**
     * SKIP LOCKED 를 명시적으로 사용하는 native 쿼리.
     * MySQL 8.0+ 가정.
     */
    @Query(value = """
           SELECT * FROM outbox_entry
           WHERE status = 'PENDING'
           ORDER BY id ASC
           LIMIT :limit
           FOR UPDATE SKIP LOCKED
           """, nativeQuery = true)
    List<OutboxEntry> findPendingForRelay(@Param("limit") int limit);
}
