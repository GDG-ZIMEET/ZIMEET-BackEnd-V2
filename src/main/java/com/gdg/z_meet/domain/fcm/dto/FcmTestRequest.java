package com.gdg.z_meet.domain.fcm.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcmTestRequest {
    private  String fcmToken;

    public FcmTestRequest(String fcmToken) {
        this.fcmToken = fcmToken;
    }
}
