package com.gdg.z_meet.payment.outbox;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdg.z_meet.common.event.PaymentEvent;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

/**
 * 결제 승인/취소/환불 트랜잭션 내부에서 호출되어,
 * 해당 이벤트를 Outbox 에 append 한다.
 *
 * 반드시 호출자의 트랜잭션에 참여해야 하므로 Propagation.MANDATORY 를 강제한다.
 * 이렇게 해야 "비즈니스 커밋 + 이벤트 저장"이 원자적으로 보장된다.
 */
@Component
@RequiredArgsConstructor
public class OutboxRecorder {

    private final OutboxRepository outboxRepository;
    private final ObjectMapper objectMapper;

    @Transactional(propagation = Propagation.MANDATORY)
    public void record(PaymentEvent event) {
        OutboxEntry entry = OutboxEntry.builder()
                .eventId(event.eventId())
                .aggregateType("Payment")
                .aggregateId(event.orderId())
                .eventType(event.getClass().getSimpleName())
                .payload(serialize(event))
                .build();
        outboxRepository.save(entry);
    }

    private String serialize(PaymentEvent event) {
        try {
            return objectMapper.writeValueAsString(event);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("Failed to serialize payment event: " + event.eventId(), e);
        }
    }
}
