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

import org.springframework.dao.DataIntegrityViolationException;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmTokenServiceImpl implements FcmTokenService {

    private final UserRepository userRepository;
    private final FcmTokenTransactionService fcmTokenTransactionService;

    /**
     * FCM 푸시 알림 사용자 동의 여부
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
     * FCM 토큰 동기화
     */
    @Override
    public void syncFcmToken(Long userId, UserReq.saveFcmTokenReq req) {
        try {
            fcmTokenTransactionService.doSyncFcmToken(userId, req);
        } catch (DataIntegrityViolationException e) {
            log.debug("FCM 토큰 동기화 중 동시성 충돌 발생, 재시도 수행. userId={}", userId);
            fcmTokenTransactionService.doSyncFcmToken(userId, req);
        }
    }
}