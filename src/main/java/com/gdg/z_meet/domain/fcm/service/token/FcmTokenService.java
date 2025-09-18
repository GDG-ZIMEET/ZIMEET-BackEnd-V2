package com.gdg.z_meet.domain.fcm.service.token;

import com.gdg.z_meet.domain.user.dto.UserReq;

public interface FcmTokenService {
    
    boolean agreePush(Long userId, UserReq.pushAgreeReq req);
    
    void syncFcmToken(Long userId, UserReq.saveFcmTokenReq req);
}
