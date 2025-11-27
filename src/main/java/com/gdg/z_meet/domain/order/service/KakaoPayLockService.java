package com.gdg.z_meet.domain.order.service;

import com.gdg.z_meet.domain.order.repository.NamedLockRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.transaction.support.TransactionSynchronization;
import org.springframework.transaction.support.TransactionSynchronizationManager;

import java.time.Duration;
import java.time.Instant;
import java.util.UUID;

/**
 * 결제 프로세스에 필요한 락 관리 담당
 * 
 * 네임드락 획득/해제가 락을 얻은 시점의 트랜잭션과 같은 세션에서 이루어져야 함.
 * MANDATORY 전파 전략을 통해 상위 트랜잭션 안에서만 호출되도록 강제
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPayLockService {

    private final NamedLockRepository namedLockRepository;
    private final KakaoPayLockMonitoringService kakaoPaylockMonitoringService;

    private static final String LOCK_PREFIX = "LOCK_KAKAO_PAY_APPROVE_";

    private static final Duration ACQUIRE_TIMEOUT = Duration.ofSeconds(3);

    /**
     * 주문 ID에 대한 락을 획득 (MySQL 네임드락)
     * - GET_LOCK(timeout)은 내부 대기 포함
     * - 성공 시 감사 로그 기록
     * - 커밋 이후(afterCommit) 해제 및 감사 로그 기록
     */
    @Transactional(propagation = Propagation.MANDATORY)
    public String acquireLock(String orderId) {
        String lockName = LOCK_PREFIX + orderId;
        String ownerId = (TransactionSynchronizationManager.getCurrentTransactionName() != null)
                ? TransactionSynchronizationManager.getCurrentTransactionName()
                : UUID.randomUUID().toString();
        Instant start = Instant.now();

        try {
            Integer result = namedLockRepository.getLock(lockName, (int) ACQUIRE_TIMEOUT.getSeconds());
            int waitMs = (int) Duration.between(start, Instant.now()).toMillis();

            if (result == null || result == 0) {
                kakaoPaylockMonitoringService.timeout(lockName, ownerId, waitMs);
                log.warn("네임드락 획득 실패/타임아웃 - lockName: {}", lockName);
                throw new BusinessException(Code.IDEMPOTENCY_CONFLICT);
            }

            // 감사: 획득 기록
            kakaoPaylockMonitoringService.acquired(lockName, ownerId, Instant.now(), waitMs);

            // 커밋 이후 해제 및 감사 기록 등록
            registerReleaseAfterCommit(lockName, ownerId, Instant.now());

            log.debug("네임드락 획득 성공 - lockName: {}, ownerId:{}", lockName, ownerId);
            return lockName;
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            kakaoPaylockMonitoringService.failed(lockName, ownerId, e.getMessage());
            throw new BusinessException(Code.INTERNAL_SERVER_ERROR);
        }
    }

    private void registerReleaseAfterCommit(String lockName, String ownerId, Instant acquiredAt) {
        TransactionSynchronizationManager.registerSynchronization(new TransactionSynchronization() {
            @Override
            public void afterCommit() {
                try {
                    namedLockRepository.releaseLock(lockName);
                    int holdMs = (int) Duration.between(acquiredAt, Instant.now()).toMillis();
                    kakaoPaylockMonitoringService.released(lockName, ownerId, Instant.now(), holdMs);
                    log.debug("커밋 후 네임드락 해제 - lockName: {}", lockName);
                } catch (Exception e) {
                    try {
                        kakaoPaylockMonitoringService.failed(lockName, ownerId, "AFTER_COMMIT_RELEASE_FAILED: " + e.getMessage());
                    } catch (Exception ignore) { }
                    log.warn("커밋 후 네임드락 해제 실패 - lockName: {}", lockName, e);
                }
            }
        });
    }
}