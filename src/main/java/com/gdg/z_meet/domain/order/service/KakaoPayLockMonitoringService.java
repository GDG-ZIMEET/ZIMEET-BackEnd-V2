package com.gdg.z_meet.domain.order.service;

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
            lockMonitoringRepository.insertAcquired(lockName, ownerId, acquiredAt, waitMs);
        } catch (Exception e) {
            log.debug("락 감사 ACQUIRED 기록 실패 - {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void released(String lockName, String ownerId, Instant releasedAt, Integer holdMs) {
        try {
            lockMonitoringRepository.insertReleased(lockName, ownerId, releasedAt, holdMs);
        } catch (Exception e) {
            log.debug("락 감사 RELEASED 기록 실패 - {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void timeout(String lockName, String ownerId, Integer waitMs) {
        try {
            lockMonitoringRepository.insertTimeout(lockName, ownerId, waitMs);
        } catch (Exception e) {
            log.debug("락 감사 TIMEOUT 기록 실패 - {}", e.getMessage());
        }
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void failed(String lockName, String ownerId, String errorMessage) {
        try {
            lockMonitoringRepository.insertFailed(lockName, ownerId, errorMessage);
        } catch (Exception e) {
            log.debug("락 감사 FAILED 기록 실패 - {}", e.getMessage());
        }
    }
}