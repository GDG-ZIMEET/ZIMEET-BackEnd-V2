package com.gdg.z_meet.common.event;

/**
 * Redis Streams 키 및 Consumer Group 이름 상수.
 * Payment/Core 양쪽에서 동일 값을 참조해야 하므로 공통 모듈에 둔다.
 */
public final class EventStreams {

    private EventStreams() {}

    /** Payment 서버가 발행, Core 서버가 소비. */
    public static final String PAYMENT_EVENTS = "payment.events";

    /** Core 서버가 발행, Payment 서버가 소비. */
    public static final String STOCK_EVENTS = "stock.events";

    /** Core 서버의 payment.events 컨슈머 그룹. */
    public static final String CORE_PAYMENT_CONSUMER_GROUP = "core-payment-consumers";

    /** Payment 서버의 stock.events 컨슈머 그룹. */
    public static final String PAYMENT_STOCK_CONSUMER_GROUP = "payment-stock-consumers";
}
