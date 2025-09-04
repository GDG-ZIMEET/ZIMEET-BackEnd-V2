package com.gdg.z_meet.domain.fcm.service;


import com.gdg.z_meet.domain.user.dto.UserReq;
import com.gdg.z_meet.domain.user.entity.User;


public interface FcmService {

    boolean agreePush(Long userId, UserReq.pushAgreeReq req);

    void syncFcmToken(Long userId, UserReq.saveFcmTokenReq req);

    void broadcastToAllUsers(String title, String body);

    void testFcmService(Long userId, String fcmToken);
}
