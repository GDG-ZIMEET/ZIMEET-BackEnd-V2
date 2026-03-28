package com.gdg.z_meet.domain.fcm.service.consumer;

import com.gdg.z_meet.domain.fcm.dto.FcmMessageRequest;
import com.gdg.z_meet.global.config.RedisStreamConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.stereotype.Component;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import java.net.InetAddress;
import java.net.UnknownHostException;

@Slf4j
@Component
@Profile("worker")
@RequiredArgsConstructor
public class RedisFcmStreamListener implements StreamListener<String, ObjectRecord<String, FcmMessageRequest>> {

    private final StreamMessageListenerContainer<String, ObjectRecord<String, FcmMessageRequest>> container;
    private final FcmMessageConsumer fcmMessageConsumer;
    private final RedisTemplate<String, Object> redisTemplate;

    @Override
    public void onMessage(ObjectRecord<String, FcmMessageRequest> record) {
        log.info("Redis Stream 메시지 수신: stream={}, id={}, payload={}",
                record.getStream(), record.getId(), record.getValue().getMessageId());

        try {
            fcmMessageConsumer.processFcmMessage(record.getValue());

            // 성공 시 ACK 전송
            redisTemplate.opsForStream().acknowledge(RedisStreamConfig.FCM_STREAM, RedisStreamConfig.FCM_GROUP,
                    record.getId());
            log.info("Redis Stream 메시지 ACK 완료: id={}", record.getId());
        } catch (Exception e) {
            log.error("Redis Stream 메시지 처리 실패: id={}, error={}", record.getId(), e.getMessage(), e);
            // ACK를 보내지 않음으로써 Pending 상태로 유지 (이후 XAUTOCLAIM에 의해 재처리 가능)
        }
    }

    @PostConstruct
    public void init() {
        createConsumerGroupIfNotExists();

        String consumerName = getConsumerName();
        container.receive(
                Consumer.from(RedisStreamConfig.FCM_GROUP, consumerName),
                StreamOffset.create(RedisStreamConfig.FCM_STREAM, ReadOffset.lastConsumed()),
                this);

        container.start();
        log.info("Redis Stream Listener 시작됨: group={}, consumer={}", RedisStreamConfig.FCM_GROUP, consumerName);
    }

    @PreDestroy
    public void destroy() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    private void createConsumerGroupIfNotExists() {
        try {
            // 스트림이 없으면 생성하고 그룹도 생성
            if (Boolean.FALSE.equals(redisTemplate.hasKey(RedisStreamConfig.FCM_STREAM))) {
                // 스트림 생성 (임의의 데이터를 넣었다가 삭제하거나, 바로 그룹 생성 명령 시 MKSTREAM 옵션 사용)
                log.info("Redis Stream 이 존재하지 않아 생성합니다: {}", RedisStreamConfig.FCM_STREAM);
            }

            redisTemplate.opsForStream().createGroup(RedisStreamConfig.FCM_STREAM, RedisStreamConfig.FCM_GROUP);
            log.info("Redis Consumer Group 생성 완료: {}", RedisStreamConfig.FCM_GROUP);
        } catch (Exception e) {
            // 이미 그룹이 있는 경우 등 예외 발생 가능 (정상)
            log.debug("Redis Consumer Group 이미 존재하거나 생성 중 오류 (무시 가능): {}", e.getMessage());
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
