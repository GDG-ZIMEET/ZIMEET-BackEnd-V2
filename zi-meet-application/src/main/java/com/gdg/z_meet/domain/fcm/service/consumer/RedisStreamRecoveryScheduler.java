package com.gdg.z_meet.domain.fcm.service.consumer;

import com.gdg.z_meet.global.config.RedisStreamConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisStreamRecoveryScheduler {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * 5분 이상 Pending된 메시지를 현재 컨슈머가 소유권을 가져와서 재처리를 유도합니다. (XAUTOCLAIM)
     */
    @Scheduled(fixedDelay = 60000) // 1분마다 실행
    public void reclaimPendingMessages() {
        String consumerName = getConsumerName();
        log.info("Redis Stream Pending 메시지 복구(XAUTOCLAIM) 시작: group={}, consumer={}",
                RedisStreamConfig.FCM_GROUP, consumerName);

        try {
            // Redis 6.2+ XAUTOCLAIM 명령어를 직접 실행합니다. (Spring Data Redis StreamOperations 에는
            // 아직 래퍼가 없을 수 있음)
            redisTemplate.execute((org.springframework.data.redis.core.RedisCallback<Object>) connection -> {
                return connection.execute("XAUTOCLAIM",
                        RedisStreamConfig.FCM_STREAM.getBytes(),
                        RedisStreamConfig.FCM_GROUP.getBytes(),
                        consumerName.getBytes(),
                        String.valueOf(Duration.ofMinutes(5).toMillis()).getBytes(),
                        "0-0".getBytes());
            });
            log.info("Redis Stream Pending 메시지 복구(XAUTOCLAIM) 완료");
        } catch (Exception e) {
            log.error("Redis Stream Pending 메시지 복구 중 에러 발생: {}", e.getMessage());
        }
    }

    private String getConsumerName() {
        try {
            return InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "unknown-consumer";
        }
    }
}
