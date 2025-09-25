package com.gdg.z_meet.domain.fcm.service.producer;


public interface FcmMessageProducer {
    
    void sendBroadcastMessage(String title, String body);
    
    void sendTestMessage(Long userId, String fcmToken, String title, String body);
    
    void sendSingleMessage(Long userId, String title, String body);
}
