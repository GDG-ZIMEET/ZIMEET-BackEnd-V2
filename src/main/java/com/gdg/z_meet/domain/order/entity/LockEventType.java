package com.gdg.z_meet.domain.order.entity;

/**
 * 락 모니터링 이벤트 타입
 */
public enum LockEventType {
    ACQUIRED,
    RELEASED,
    TIMEOUT,
    FAILED
}

