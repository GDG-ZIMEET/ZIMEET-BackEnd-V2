package com.gdg.z_meet.domain.fcm.service.token;

import com.gdg.z_meet.domain.user.dto.UserReq;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.locks.ReentrantLock;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmTokenServiceImpl implements FcmTokenService {

    private final UserRepository userRepository;
    private final FcmTokenTransactionService fcmTokenTransactionService;
    
    // 사용자별 동기화를 위한 락 맵
    private final Map<Long, ReentrantLock> userLocks = new ConcurrentHashMap<>();

    /**
     *  FCM 푸시 알림 사용자 동의 여부
     */
    @Override
    @Transactional
    public boolean agreePush(Long userId, UserReq.pushAgreeReq req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Code.USER_NOT_FOUND));

        user.setPushAgree(req.isPushAgree());
        return req.isPushAgree();
    }

    /**
     * FCM 토큰 동기화 (동시성 안전)
     * 
     * 동시성 문제 해결 전략:
     * 1. 사용자별 동기화 락으로 동시성 보장
     * 2. 외부 트랜잭션에서 락 관리
     * 3. REQUIRES_NEW로 독립 트랜잭션에서 DB 작업 수행
     * 4. findByUserForUpdate로 락 획득하여 토큰 처리
     * 5. 락이 걸린 상태에서 안전하게 업데이트/생성
     */
    @Override
    public void syncFcmToken(Long userId, UserReq.saveFcmTokenReq req) {
        // 사용자별 동기화 락 획득
        ReentrantLock lock = userLocks.computeIfAbsent(userId, k -> new ReentrantLock());
        lock.lock();

        try {
            fcmTokenTransactionService.doSyncFcmToken(userId, req);
        } finally {
            lock.unlock();

            // 필요 시 조건부 제거(경합이 없을 때만)
            if(!lock.isLocked() && !lock.hasQueuedThreads()){
                userLocks.remove(userId, lock);
            }
        }
    }
}