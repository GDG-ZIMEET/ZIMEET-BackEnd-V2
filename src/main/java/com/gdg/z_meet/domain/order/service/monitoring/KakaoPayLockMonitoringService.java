package com.gdg.z_meet.domain.order.service.monitoring;

import com.gdg.z_meet.domain.order.entity.LockEventType;
import com.gdg.z_meet.domain.order.entity.LockMonitoring;
import com.gdg.z_meet.domain.order.repository.LockMonitoringRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPayLockMonitoringService {

    private final LockMonitoringRepository lockMonitoringRepository;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void acquired(String lockName, String ownerId, Instant acquiredAt, Integer waitMs) {
        try {
            insertAcquired(lockName, ownerId, acquiredAt, waitMs);
        } catch (Exception e) {
            log.debug("락 감사 ACQUIRED 기록 실패 - {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void released(String lockName, String ownerId, Instant releasedAt, Integer holdMs) {
        try {
            insertReleased(lockName, ownerId, releasedAt, holdMs);
        } catch (Exception e) {
            log.debug("락 감사 RELEASED 기록 실패 - {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void timeout(String lockName, String ownerId, Integer waitMs) {
        try {
            insertTimeout(lockName, ownerId, waitMs);
        } catch (Exception e) {
            log.debug("락 감사 TIMEOUT 기록 실패 - {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(String lockName, String ownerId, String errorMessage) {
        try {
            insertFailed(lockName, ownerId, errorMessage);
        } catch (Exception e) {
            log.debug("락 감사 FAILED 기록 실패 - {}", e.getMessage());
        }
    }

    /**
     * 락 이벤트 모니터링을 위한 헬퍼 메서드
     */
    private void insertAcquired(String lockName, String ownerId, Instant acquiredAt, Integer waitMs) {
        LockMonitoring monitoring = new LockMonitoring();
        monitoring.setLockName(lockName);
        monitoring.setOwnerId(ownerId);
        monitoring.setEvent(LockEventType.ACQUIRED);
        monitoring.setAcquiredAt(acquiredAt);
        monitoring.setWaitMs(waitMs != null ? waitMs.longValue() : null);
        monitoring.setCreatedAt(Instant.now());
        lockMonitoringRepository.save(monitoring);
    }

    private void insertReleased(String lockName, String ownerId, Instant releasedAt, Integer holdMs) {
        LockMonitoring monitoring = new LockMonitoring();
        monitoring.setLockName(lockName);
        monitoring.setOwnerId(ownerId);
        monitoring.setEvent(LockEventType.RELEASED);
        monitoring.setReleasedAt(releasedAt);
        monitoring.setHoldMs(holdMs != null ? holdMs.longValue() : null);
        monitoring.setCreatedAt(Instant.now());
        lockMonitoringRepository.save(monitoring);
    }

    private void insertTimeout(String lockName, String ownerId, Integer waitMs) {
        LockMonitoring monitoring = new LockMonitoring();
        monitoring.setLockName(lockName);
        monitoring.setOwnerId(ownerId);
        monitoring.setEvent(LockEventType.TIMEOUT);
        monitoring.setWaitMs(waitMs != null ? waitMs.longValue() : null);
        monitoring.setCreatedAt(Instant.now());
        lockMonitoringRepository.save(monitoring);
    }

    private void insertFailed(String lockName, String ownerId, String errorMessage) {
        LockMonitoring monitoring = new LockMonitoring();
        monitoring.setLockName(lockName);
        monitoring.setOwnerId(ownerId);
        monitoring.setEvent(LockEventType.FAILED);
        monitoring.setErrorMessage(errorMessage);
        monitoring.setCreatedAt(Instant.now());
        lockMonitoringRepository.save(monitoring);
    }
}