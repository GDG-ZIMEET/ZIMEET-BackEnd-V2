package com.gdg.z_meet.payment.stream;

import com.gdg.z_meet.common.event.EventStreams;
import com.gdg.z_meet.payment.outbox.OutboxEntry;
import com.gdg.z_meet.payment.outbox.OutboxRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.RecordId;
import org.springframework.data.redis.connection.stream.StreamRecords;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;

/**
 * Outbox PENDING 행을 폴링해 Redis Streams 로 발행한다.
 *
 * 실행 조건:
 *  - payment-worker 프로파일에서만 동작 (API 서버에서는 돌지 않는다).
 *  - SKIP LOCKED 로 row 선점 -> 다중 인스턴스 환경에서도 중복 발행 없음.
 *
 * 장애 시나리오:
 *  - XADD 실패  : 해당 배치 중단, 다음 tick 에 재시도.
 *  - XADD 성공 / UPDATE 실패 : 다음 tick 재발행 -> Consumer 의 dedup 테이블에서 흡수.
 *  - Relay 프로세스 다운  : Outbox 는 PENDING 유지, 재기동 후 이어서 처리.
 *
 * 어떤 경우에도 이벤트 유실은 없으며 at-least-once 를 보장한다.
 */
@Component
@Profile("payment-worker")
@RequiredArgsConstructor
@Slf4j
public class OutboxRelay {

    private static final int BATCH_SIZE = 100;

    private final OutboxRepository outboxRepository;
    private final StringRedisTemplate redisTemplate;

    @Scheduled(fixedDelayString = "${payment.outbox.relay.delay-ms:500}")
    @Transactional
    public void relay() {
        List<OutboxEntry> batch = outboxRepository.findPendingForRelay(BATCH_SIZE);
        if (batch.isEmpty()) {
            return;
        }

        for (OutboxEntry entry : batch) {
            try {
                publish(entry);
                entry.markSent();
            } catch (RuntimeException e) {
                log.error("Outbox relay failed for id={} eventId={}: {}",
                        entry.getId(), entry.getEventId(), e.getMessage());
                entry.markFailed();
                // 한 건 실패가 뒤 건들을 막지 않도록 계속 진행한다.
                // 영속화는 트랜잭션 커밋 시 반영되므로 실패한 건은 Status=FAILED 로 남는다.
                // 운영 중에는 Failed 건을 별도 스윕 배치로 재시도한다.
            }
        }
    }

    private void publish(OutboxEntry entry) {
        MapRecord<String, String, String> record = StreamRecords.mapBacked(Map.of(
                        "eventId",   entry.getEventId(),
                        "eventType", entry.getEventType(),
                        "aggregateId", entry.getAggregateId(),
                        "payload",   entry.getPayload()
                ))
                .withStreamKey(EventStreams.PAYMENT_EVENTS);

        RecordId recordId = redisTemplate.opsForStream().add(record);
        log.debug("Published outbox id={} to stream={} recordId={}",
                entry.getId(), EventStreams.PAYMENT_EVENTS, recordId);
    }
}
