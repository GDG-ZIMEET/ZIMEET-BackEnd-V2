package com.gdg.z_meet.core.stream;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Payment -> Core 스트림 컨슈머의 idempotency 보장용 테이블.
 *
 * 컨슈머는 메시지 수신 시 첫 단계에서 이 테이블에 INSERT 를 시도한다.
 * eventId 가 PK 이므로 중복 메시지는 DataIntegrityViolationException 으로 거절된다.
 * 그 경우 본 처리 없이 즉시 ACK 만 전송한다 (at-least-once -> exactly-once 변환).
 */
@Entity
@Table(name = "processed_events")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class ProcessedEvent {

    @Id
    @Column(name = "event_id", nullable = false, length = 64)
    private String eventId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt;

    @Builder
    private ProcessedEvent(String eventId, String eventType) {
        this.eventId = eventId;
        this.eventType = eventType;
        this.processedAt = Instant.now();
    }
}
