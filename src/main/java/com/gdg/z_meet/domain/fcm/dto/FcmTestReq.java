package com.gdg.z_meet.domain.fcm.dto;

import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class FcmTestReq {
    private  String fcmToken;

    public FcmTestReq(String fcmToken) {
        this.fcmToken = fcmToken;
    }
}
