package com.gdg.z_meet.domain.order.entity;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.Instant;

/**
 * 락 감사 로그를 위한 Entity
 * 락 획득/해제/타임아웃/실패 이벤트를 추적하기 위한 테이블
 */
@Entity
@Table(name = "lock_audit")
@Getter
@NoArgsConstructor
public class LockAudit {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(name = "lock_name", nullable = false)
    private String lockName;

    @Column(name = "owner_id", nullable = false)
    private String ownerId;

    @Column(name = "event", nullable = false, length = 50)
    private String event;

    @Column(name = "acquired_at")
    private Instant acquiredAt;

    @Column(name = "released_at")
    private Instant releasedAt;

    @Column(name = "wait_ms")
    private Integer waitMs;

    @Column(name = "hold_ms")
    private Integer holdMs;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;
}