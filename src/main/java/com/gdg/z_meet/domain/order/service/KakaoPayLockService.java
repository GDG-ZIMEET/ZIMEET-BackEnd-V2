package com.gdg.z_meet.domain.order.service;

import com.gdg.z_meet.domain.order.repository.NamedLockRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

/**
 * 결제 프로세스에 필요한 락 관리 담당
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPayLockService {

    private final NamedLockRepository namedLockRepository;

    private static final String LOCK_PREFIX = "LOCK_KAKAO_PAY_APPROVE_";

    /**
     * 주문 ID에 대한 락을 획득
     * @param orderId 주문 ID
     * @return 락 이름
     * @throws BusinessException 락 획득 실패 시
     */
    public String acquireLock(String orderId) {
        String lockName = LOCK_PREFIX + orderId;
        log.debug("락 요청 - lockName: {}", lockName);
        
        Integer result = namedLockRepository.getLock(lockName);
        
        if (result == null || result == 0) {
            log.error("락 획득 실패 - lockName: {}", lockName);
            throw new BusinessException(Code.INTERNAL_SERVER_ERROR);
        }
        
        log.debug("락 획득 성공 - lockName: {}", lockName);
        return lockName;
    }

    /**
     * 락을 해제
     * @param lockName 락 이름
     */
    public void releaseLock(String lockName) {
        log.debug("락 해제 - lockName: {}", lockName);
        namedLockRepository.releaseLock(lockName);
    }
}

