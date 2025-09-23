package com.gdg.z_meet.domain.chat.redis;

import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class RedisPublisher {

    private final RedisTemplate<String, Object> redisTemplate;

    // 메시지를 발행
    public void publishToRoom(Long roomId, Object message) {
        String channel = "chat:room:" + roomId;
        redisTemplate.convertAndSend(channel, message);
    }

}
