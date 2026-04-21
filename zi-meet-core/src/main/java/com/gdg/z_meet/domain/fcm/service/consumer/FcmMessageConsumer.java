package com.gdg.z_meet.domain.fcm.service.consumer;

import com.gdg.z_meet.domain.fcm.dto.FcmMessageRequest;

public interface FcmMessageConsumer {
    void processFcmMessage(FcmMessageRequest fcmMessage);

}
