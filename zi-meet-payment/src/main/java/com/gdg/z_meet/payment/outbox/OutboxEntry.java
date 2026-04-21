package com.gdg.z_meet.payment.outbox;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * Transactional Outbox 레코드.
 *
 * 결제 승인 트랜잭션에서 본 테이블에 row 를 INSERT 함으로써,
 * "DB 커밋"과 "발행할 이벤트의 영속화"가 같은 트랜잭션 안에서 원자적으로 이루어진다.
 *
 * Redis Streams XADD 는 별도의 Relay 가 PENDING 행을 폴링하여 수행하므로,
 * MySQL <-> Redis 사이에 분산 트랜잭션이 필요 없다.
 * 중복 발행은 Consumer 쪽 processed_events 테이블의 unique 제약으로 흡수한다.
 */
@Entity
@Table(
        name = "outbox_entry",
        indexes = {
                @Index(name = "idx_outbox_status_id", columnList = "status, id"),
                @Index(name = "idx_outbox_event_id", columnList = "event_id", unique = true)
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class OutboxEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "event_id", nullable = false, length = 36, unique = true)
    private String eventId;

    @Column(name = "aggregate_type", nullable = false, length = 64)
    private String aggregateType;

    @Column(name = "aggregate_id", nullable = false, length = 64)
    private String aggregateId;

    @Column(name = "event_type", nullable = false, length = 64)
    private String eventType;

    @Column(name = "payload", nullable = false, columnDefinition = "JSON")
    private String payload;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 16)
    private Status status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "sent_at")
    private Instant sentAt;

    @Builder
    private OutboxEntry(String eventId,
                        String aggregateType,
                        String aggregateId,
                        String eventType,
                        String payload) {
        this.eventId = eventId;
        this.aggregateType = aggregateType;
        this.aggregateId = aggregateId;
        this.eventType = eventType;
        this.payload = payload;
        this.status = Status.PENDING;
        this.retryCount = 0;
        this.createdAt = Instant.now();
    }

    public void markSent() {
        this.status = Status.SENT;
        this.sentAt = Instant.now();
    }

    public void markFailed() {
        this.status = Status.FAILED;
        this.retryCount++;
    }

    public void reopen() {
        this.status = Status.PENDING;
        this.retryCount++;
    }

    public enum Status {
        PENDING, SENT, FAILED
    }
}
