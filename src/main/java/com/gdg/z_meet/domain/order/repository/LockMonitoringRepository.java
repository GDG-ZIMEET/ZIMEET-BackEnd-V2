package com.gdg.z_meet.domain.order.repository;

import java.time.Instant;

public interface LockMonitoringRepository {

    int insertAcquired(String lockName, String ownerId, Instant acquiredAt, Integer waitMs);

    int insertReleased(String lockName, String ownerId, Instant releasedAt, Integer holdMs);

    int insertTimeout(String lockName, String ownerId, Integer waitMs);

    int insertFailed(String lockName, String ownerId, String errorMessage);
}


