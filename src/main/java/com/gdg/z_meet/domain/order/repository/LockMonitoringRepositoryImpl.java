package com.gdg.z_meet.domain.order.repository;

import jakarta.persistence.EntityManager;
import jakarta.persistence.PersistenceContext;
import jakarta.persistence.Query;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
public class LockMonitoringRepositoryImpl implements LockMonitoringRepository {

    @PersistenceContext
    private EntityManager entityManager;

    @Override
    public int insertAcquired(String lockName, String ownerId, Instant acquiredAt, Integer waitMs) {
        Query q = entityManager.createNativeQuery("INSERT INTO lock_audit(lock_name, owner_id, event, acquired_at, wait_ms, created_at) VALUES (?, ?, 'ACQUIRED', ?, ?, NOW(6))");
        q.setParameter(1, lockName);
        q.setParameter(2, ownerId);
        q.setParameter(3, java.sql.Timestamp.from(acquiredAt));
        q.setParameter(4, waitMs);
        return q.executeUpdate();
    }

    @Override
    public int insertReleased(String lockName, String ownerId, Instant releasedAt, Integer holdMs) {
        Query q = entityManager.createNativeQuery("INSERT INTO lock_audit(lock_name, owner_id, event, released_at, hold_ms, created_at) VALUES (?, ?, 'RELEASED', ?, ?, NOW(6))");
        q.setParameter(1, lockName);
        q.setParameter(2, ownerId);
        q.setParameter(3, java.sql.Timestamp.from(releasedAt));
        q.setParameter(4, holdMs);
        return q.executeUpdate();
    }

    @Override
    public int insertTimeout(String lockName, String ownerId, Integer waitMs) {
        Query q = entityManager.createNativeQuery("INSERT INTO lock_audit(lock_name, owner_id, event, wait_ms, created_at) VALUES (?, ?, 'TIMEOUT', ?, NOW(6))");
        q.setParameter(1, lockName);
        q.setParameter(2, ownerId);
        q.setParameter(3, waitMs);
        return q.executeUpdate();
    }

    @Override
    public int insertFailed(String lockName, String ownerId, String errorMessage) {
        Query q = entityManager.createNativeQuery("INSERT INTO lock_audit(lock_name, owner_id, event, created_at) VALUES (?, ?, 'FAILED', NOW(6))");
        q.setParameter(1, lockName);
        q.setParameter(2, ownerId);
        return q.executeUpdate();
    }
}