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
import org.springframework.transaction.annotation.Transactional;

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
     *  FCM 코큰 갱신
     */
    @Override
    @Transactional
    public void syncFcmToken(Long userId, UserReq.saveFcmTokenReq req) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Code.USER_NOT_FOUND));

        if (!user.isPushAgree()) { 
            throw new BusinessException(Code.FCM_PUSH_NOT_AGREED);
        }

        String newToken = req.getFcmToken();
        FcmToken token = fcmTokenRepository.findByUser(user).orElse(null);

        if (token == null) {
            fcmTokenRepository.save(FcmToken.builder()
                    .user(user)
                    .token(newToken)
                    .build());
            return;
        }

        // 토큰이 다를 때만 갱신
        if (!newToken.equals(token.getToken())) {
            token.setToken(newToken);
        }
    }
}
