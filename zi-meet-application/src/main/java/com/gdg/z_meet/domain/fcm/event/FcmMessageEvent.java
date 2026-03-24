package com.gdg.z_meet.domain.fcm.event;

import com.gdg.z_meet.domain.fcm.dto.FcmMessageRequest;
import lombok.Getter;
import lombok.RequiredArgsConstructor;

/**
 * FCM 메시지 발행을 위한 내부 이벤트
 */
@Getter
@RequiredArgsConstructor
public class FcmMessageEvent {
    private final String routingKey;
    private final FcmMessageRequest message;
}
