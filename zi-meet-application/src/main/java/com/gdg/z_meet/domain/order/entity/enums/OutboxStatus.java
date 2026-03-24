package com.gdg.z_meet.domain.order.entity.enums;

import lombok.Getter;

@Getter
public enum OutboxStatus {
    INIT("발행 대기"),
    PUBLISHED("발행 완료"),
    FAILED("발행 실패");

    private final String description;

    OutboxStatus(String description) {
        this.description = description;
    }
}