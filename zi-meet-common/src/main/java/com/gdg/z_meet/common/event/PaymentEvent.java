package com.gdg.z_meet.common.event;

import java.time.Instant;

/**
 * Payment 서버가 Core 서버로 발행하는 이벤트의 공통 계약.
 *
 * 모든 이벤트는 멱등 처리를 위한 고유 eventId를 가진다.
 * 발행은 Payment 서버의 Outbox -> Redis Streams Relay를 통해 at-least-once로 이루어진다.
 * Consumer는 eventId 기반 dedup 테이블을 사용해 중복 처리를 방지한다.
 */
public sealed interface PaymentEvent
        permits PaymentEvent.PaymentApproved,
                PaymentEvent.PaymentCancelled,
                PaymentEvent.PaymentRefunded,
                PaymentEvent.PaymentFailed {

    String eventId();

    String orderId();

    Instant occurredAt();

    /** 결제 승인 완료. Core는 재고 확정 + 상품 지급을 수행한다. */
    record PaymentApproved(
            String eventId,
            String orderId,
            Long userId,
            String productType,
            int quantity,
            long totalPrice,
            Instant occurredAt
    ) implements PaymentEvent {}

    /** 결제 취소. Core는 예약 해제 및 지급 롤백을 수행한다. */
    record PaymentCancelled(
            String eventId,
            String orderId,
            Long userId,
            String reason,
            Instant occurredAt
    ) implements PaymentEvent {}

    /** 결제 환불. Core는 상품 회수(필요 시) 및 상태 동기화를 수행한다. */
    record PaymentRefunded(
            String eventId,
            String orderId,
            Long userId,
            long refundAmount,
            Instant occurredAt
    ) implements PaymentEvent {}

    /**
     * 결제 실패(Abandoned 포함).
     * Ready 단계에서 만료·이탈된 결제에 대해서도 발행되어,
     * Core가 예약 재고를 복원할 수 있게 한다.
     */
    record PaymentFailed(
            String eventId,
            String orderId,
            Long userId,
            String reason,
            Instant occurredAt
    ) implements PaymentEvent {}
}
