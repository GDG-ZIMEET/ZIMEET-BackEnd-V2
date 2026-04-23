package com.gdg.z_meet.core.stream;

import com.gdg.z_meet.common.event.EventStreams;
import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.redis.connection.stream.Consumer;
import org.springframework.data.redis.connection.stream.MapRecord;
import org.springframework.data.redis.connection.stream.ReadOffset;
import org.springframework.data.redis.connection.stream.StreamOffset;
import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.data.redis.stream.StreamListener;
import org.springframework.data.redis.stream.StreamMessageListenerContainer;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.net.InetAddress;
import java.net.UnknownHostException;
import java.time.Duration;

/**
 * Payment 서버가 Outbox Relay 로 발행한 payment.events 스트림을 소비한다.
 *
 * 실행 조건:
 *  - core-worker 프로파일에서만 동작 (API 서버에서는 돌지 않는다).
 *
 * 멱등성:
 *  - 메시지 처리 첫 단계에서 processed_events 테이블에 INSERT 를 시도.
 *  - 중복 메시지라면 DataIntegrityViolationException 으로 거절 -> 본 처리 건너뛰고 ACK 만 전송.
 *  - 이를 통해 at-least-once 전송을 exactly-once 처리로 변환.
 *
 * 실패 시 ACK 를 보내지 않아 pending 상태가 유지되며,
 * 운영 단계에서는 XAUTOCLAIM 기반 재처리 전략을 추가한다 (추후 Phase).
 */
@Slf4j
@Component
@Profile("core-worker")
@RequiredArgsConstructor
public class PaymentEventsConsumer implements StreamListener<String, MapRecord<String, String, String>> {

    private final StreamMessageListenerContainer<String, MapRecord<String, String, String>> container;
    private final StringRedisTemplate redisTemplate;
    private final ProcessedEventRepository processedEventRepository;

    @Override
    @Transactional
    public void onMessage(MapRecord<String, String, String> record) {
        String eventId = record.getValue().get("eventId");
        String eventType = record.getValue().get("eventType");
        String payload = record.getValue().get("payload");

        if (eventId == null || eventType == null) {
            log.warn("Malformed payment event - recordId={} body={}", record.getId(), record.getValue());
            ack(record);
            return;
        }

        try {
            processedEventRepository.save(ProcessedEvent.builder()
                    .eventId(eventId)
                    .eventType(eventType)
                    .build());
        } catch (DataIntegrityViolationException dup) {
            log.info("Duplicate payment event - eventId={} type={} (skip)", eventId, eventType);
            ack(record);
            return;
        }

        try {
            dispatch(eventType, payload);
            ack(record);
        } catch (RuntimeException e) {
            log.error("Payment event handling failed - eventId={} type={}: {}",
                    eventId, eventType, e.getMessage(), e);
            // ACK 생략 -> pending 상태 유지 -> 재처리 대상.
            // processed_events INSERT 는 트랜잭션 롤백으로 함께 되돌아간다.
            throw e;
        }
    }

    private void dispatch(String eventType, String payload) {
        // 실제 도메인 핸들러는 Phase 2-B 에서 order/settlement 코드가 payment 로 이동된 뒤
        // 재고 확정 / 환불 보상 / 재고 복원 로직과 연결한다. 현재는 로그만 남긴다.
        switch (eventType) {
            case "PaymentApproved"  -> log.info("PaymentApproved received: {}", payload);
            case "PaymentCancelled" -> log.info("PaymentCancelled received: {}", payload);
            case "PaymentRefunded"  -> log.info("PaymentRefunded received: {}", payload);
            case "PaymentFailed"    -> log.info("PaymentFailed received: {}", payload);
            default -> log.warn("Unknown payment event type: {}", eventType);
        }
    }

    private void ack(MapRecord<String, String, String> record) {
        redisTemplate.opsForStream().acknowledge(
                EventStreams.PAYMENT_EVENTS,
                EventStreams.CORE_PAYMENT_CONSUMER_GROUP,
                record.getId());
    }

    @PostConstruct
    public void start() {
        ensureGroup();
        container.receive(
                Consumer.from(EventStreams.CORE_PAYMENT_CONSUMER_GROUP, consumerName()),
                StreamOffset.create(EventStreams.PAYMENT_EVENTS, ReadOffset.lastConsumed()),
                this);
        container.start();
        log.info("PaymentEventsConsumer started: stream={} group={}",
                EventStreams.PAYMENT_EVENTS, EventStreams.CORE_PAYMENT_CONSUMER_GROUP);
    }

    @PreDestroy
    public void stop() {
        if (container != null && container.isRunning()) {
            container.stop();
        }
    }

    private void ensureGroup() {
        try {
            redisTemplate.opsForStream().createGroup(
                    EventStreams.PAYMENT_EVENTS,
                    ReadOffset.from("0"),
                    EventStreams.CORE_PAYMENT_CONSUMER_GROUP);
        } catch (Exception e) {
            log.debug("Consumer group already exists or create failed (safe to ignore): {}", e.getMessage());
        }
    }

    private static String consumerName() {
        try {
            return "core-worker-" + InetAddress.getLocalHost().getHostName();
        } catch (UnknownHostException e) {
            return "core-worker-unknown";
        }
    }
}
