package com.gdg.z_meet.domain.order.ledger;

import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface PaymentProjectionOutboxRepository extends JpaRepository<PaymentProjectionOutbox, Long> {

    @Query(value = """
           SELECT *
           FROM payment_projection_outbox
           WHERE status = 'PENDING'
           ORDER BY outbox_id ASC
           LIMIT :limit
           FOR UPDATE SKIP LOCKED
           """, nativeQuery = true)
    List<PaymentProjectionOutbox> findPendingForUpdate(@Param("limit") int limit);

    long countByStatus(ProjectionOutboxStatus status);
}
