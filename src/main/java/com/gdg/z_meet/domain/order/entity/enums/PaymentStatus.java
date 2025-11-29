package com.gdg.z_meet.domain.order.entity.enums;

import lombok.Getter;

@Getter
public enum PaymentStatus {
    PREPARED("결제 준비 완료"),
    PROCESSING("결제 처리 중"),
    APPROVED("결제 승인 완료"),
    CANCELLED("결제 취소"),
    FAILED("결제 실패");

    private final String description;

    PaymentStatus(String description) {
        this.description = description;
    }
}

