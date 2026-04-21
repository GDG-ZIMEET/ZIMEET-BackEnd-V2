package com.gdg.z_meet.domain.fcm.service.token;

import com.gdg.z_meet.domain.fcm.entity.FcmToken;
import com.gdg.z_meet.domain.fcm.repository.FcmTokenRepository;
import com.gdg.z_meet.domain.user.dto.UserReq;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

/**
 * FCM 토큰 동기화를 위한 트랜잭션 전용 서비스
 * 
 * REQUIRES_NEW 전파 전략을 별도 빈으로 분리하여
 * 외부 트랜잭션과 독립적으로 동작하도록 함
 */
@Service
@RequiredArgsConstructor
@Slf4j
public class FcmTokenTransactionService {

    private final UserRepository userRepository;
    private final FcmTokenRepository fcmTokenRepository;

    /**
     * 실제 FCM 토큰 동기화 로직 (REQUIRES_NEW 전파 전략)
     * 
     * 외부 트랜잭션과 독립적으로 새로운 트랜잭션을 시작하여
     * 동시성 문제를 해결함
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void doSyncFcmToken(Long userId, UserReq.saveFcmTokenReq req) {
        // 사용자 검증
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Code.USER_NOT_FOUND));

        if (!user.isPushAgree()) {
            throw new BusinessException(Code.FCM_PUSH_NOT_AGREED);
        }

        String newToken = req.getFcmToken();

        // 기존 토큰 조회 (비관적 락 적용)
        // 하지만 데이터가 없는 상태에서는 락을 걸 대상이 없어 동시 삽입을 막지 못함
        Optional<FcmToken> existingOpt = fcmTokenRepository.findByUserForUpdate(user);

        if (existingOpt.isPresent()) {
            FcmToken existing = existingOpt.get();
            // 토큰이 다르면 업데이트
            if (!existing.getToken().equals(newToken)) {
                existing.updateToken(newToken);
                log.debug("FCM 토큰 업데이트 완료: userId={}", user.getId());
            } else {
                log.debug("FCM 토큰 동일, 업데이트 생략: userId={}", user.getId());
            }
        } else {
            // 없으면 새로 생성
            fcmTokenRepository.save(
                    FcmToken.builder()
                            .user(user)
                            .token(newToken)
                            .build());
            log.debug("FCM 토큰 생성 완료: userId={}", user.getId());
        }
    }
}
