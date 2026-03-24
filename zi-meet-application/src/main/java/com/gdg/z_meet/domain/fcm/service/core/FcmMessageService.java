package com.gdg.z_meet.domain.fcm.service.core;

public interface FcmMessageService {
    
    void broadcastToAllUsers(String title, String body);
    
    void testFcmService(Long userId, String fcmToken);
}
