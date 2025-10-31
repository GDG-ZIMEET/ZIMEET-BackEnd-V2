package com.gdg.z_meet.domain.order.repository;

import com.gdg.z_meet.domain.order.entity.LockAudit;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.time.Instant;

@Repository
@RequiredArgsConstructor
public class LockMonitoringRepositoryImpl implements LockMonitoringRepository {

    private final LockAuditRepository lockAuditRepository;

    @Override
    public int insertAcquired(String lockName, String ownerId, Instant acquiredAt, Integer waitMs) {
        LockAudit audit = new LockAudit();
        audit.setLockName(lockName);
        audit.setOwnerId(ownerId);
        audit.setEvent("ACQUIRED");
        audit.setAcquiredAt(acquiredAt);
        audit.setWaitMs(waitMs != null ? waitMs.longValue() : null);
        audit.setCreatedAt(Instant.now());
        lockAuditRepository.save(audit);
        return 1;
    }

    @Override
    public int insertReleased(String lockName, String ownerId, Instant releasedAt, Integer holdMs) {
        LockAudit audit = new LockAudit();
        audit.setLockName(lockName);
        audit.setOwnerId(ownerId);
        audit.setEvent("RELEASED");
        audit.setReleasedAt(releasedAt);
        audit.setHoldMs(holdMs != null ? holdMs.longValue() : null);
        audit.setCreatedAt(Instant.now());
        lockAuditRepository.save(audit);
        return 1;
    }

    @Override
    public int insertTimeout(String lockName, String ownerId, Integer waitMs) {
        LockAudit audit = new LockAudit();
        audit.setLockName(lockName);
        audit.setOwnerId(ownerId);
        audit.setEvent("TIMEOUT");
        audit.setWaitMs(waitMs != null ? waitMs.longValue() : null);
        audit.setCreatedAt(Instant.now());
        lockAuditRepository.save(audit);
        return 1;
    }

    @Override
    public int insertFailed(String lockName, String ownerId, String errorMessage) {
        LockAudit audit = new LockAudit();
        audit.setLockName(lockName);
        audit.setOwnerId(ownerId);
        audit.setEvent("FAILED");
        audit.setErrorMessage(errorMessage);
        audit.setCreatedAt(Instant.now());
        lockAuditRepository.save(audit);
        return 1;
    }
}