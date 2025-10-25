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

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmTokenServiceImpl implements FcmTokenService {

    private final UserRepository userRepository;
    private final FcmTokenRepository fcmTokenRepository;

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
     * 1. 사용자 검증 후 단일 트랜잭션에서 처리
     * 2. findByUserForUpdate로 락 획득하여 토큰 처리
     * 3. 락이 걸린 상태에서 안전하게 업데이트/생성
     */
    @Override
    @Transactional
    public void syncFcmToken(Long userId, UserReq.saveFcmTokenReq req) {
        // 사용자 검증
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Code.USER_NOT_FOUND));

        if (!user.isPushAgree()) {
            throw new BusinessException(Code.FCM_PUSH_NOT_AGREED);
        }

        String newToken = req.getFcmToken();

        // 기존 토큰 조회 및 락 획득
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
                            .build()
            );
            log.debug("FCM 토큰 생성 완료: userId={}", user.getId());
        }
    }
}
