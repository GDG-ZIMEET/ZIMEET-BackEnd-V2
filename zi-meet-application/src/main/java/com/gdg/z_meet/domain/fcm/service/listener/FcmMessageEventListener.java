package com.gdg.z_meet.domain.fcm.service.listener;

import com.gdg.z_meet.domain.fcm.dto.FcmMessageRequest;
import com.gdg.z_meet.domain.fcm.event.FcmMessageEvent;
import com.gdg.z_meet.global.config.RedisStreamConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.stream.ObjectRecord;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 트랜잭션 커밋 이후에 실제로 Redis Stream으로 메시지를 전송하는 리스너
 */
@Component
@Profile("!local")
@RequiredArgsConstructor
@Slf4j
public class FcmMessageEventListener {

    private final RedisTemplate<String, Object> redisTemplate;

    /**
     * phase = TransactionPhase.AFTER_COMMIT: 트랜잭션이 성공적으로 커밋된 후에 실행
     * fallbackExecution = true: 트랜잭션이 없는 환경에서 호출되어도 실행되도록 설정
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleFcmMessageEvent(FcmMessageEvent event) {
        log.info("트랜잭션 커밋 감지 - Redis Stream 메시지 전송 시작: messageId={}",
                event.getMessage().getMessageId());

        try {
            FcmMessageRequest message = event.getMessage();

            // Redis Stream 에 ObjectRecord 형태로 저장
            ObjectRecord<String, FcmMessageRequest> record = StreamRecords.newRecord()
                    .in(RedisStreamConfig.FCM_STREAM)
                    .ofObject(message);

            redisTemplate.opsForStream().add(record);

            log.info("Redis Stream 메시지 전송 완료: messageId={}", message.getMessageId());
        } catch (Exception e) {
            log.error("Redis Stream 메시지 전송 실패: messageId={}, error={}",
                    event.getMessage().getMessageId(), e.getMessage(), e);
        }
    }
}
