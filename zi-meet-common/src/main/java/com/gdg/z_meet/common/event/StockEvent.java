package com.gdg.z_meet.common.event;

import java.time.Instant;

/**
 * Core 서버가 Payment 서버로 발행하는 재고 관련 이벤트.
 *
 * Core는 티켓 재고를 소유하므로, 결제 Ready 시 재고를 예약하고
 * 결제 Approve/Cancel 결과에 따라 확정/복원한다.
 *
 * 재고 확정/복원은 Core 내부에서 이루어지므로 Payment 에는 알릴 필요가 없지만,
 * 재고 부족으로 결제를 거부해야 하는 경우 StockShortage 이벤트로 Payment 에 통지한다.
 */
public sealed interface StockEvent
        permits StockEvent.StockReserved,
                StockEvent.StockShortage {

    String eventId();

    String orderId();

    Instant occurredAt();

    /** 재고 예약 성공. Payment 는 결제 Ready 를 이어간다. */
    record StockReserved(
            String eventId,
            String orderId,
            String skuKey,
            int quantity,
            Instant expireAt,
            Instant occurredAt
    ) implements StockEvent {}

    /** 재고 부족. Payment 는 결제를 즉시 실패 처리한다. */
    record StockShortage(
            String eventId,
            String orderId,
            String skuKey,
            int requested,
            int available,
            Instant occurredAt
    ) implements StockEvent {}
}
