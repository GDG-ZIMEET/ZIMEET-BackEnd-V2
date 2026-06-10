package com.gdg.z_meet.domain.order.ledger;

import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.entity.enums.ProductType;
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

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_ledger",
        indexes = {
                @Index(name = "idx_payment_ledger_order_id", columnList = "order_id, ledger_id"),
                @Index(name = "idx_payment_ledger_occurred_at", columnList = "occurred_at"),
                @Index(name = "idx_payment_ledger_event_type", columnList = "event_type")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentLedgerEntry {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "ledger_id")
    private Long ledgerId;

    @Column(name = "source_payment_id")
    private Long sourcePaymentId;

    @Column(name = "order_id", nullable = false, length = 80)
    private String orderId;

    @Column(name = "pg_tid", length = 80)
    private String pgTid;

    @Enumerated(EnumType.STRING)
    @Column(name = "event_type", nullable = false, length = 40)
    private PaymentLedgerEventType eventType;

    @Enumerated(EnumType.STRING)
    @Column(name = "previous_status", length = 30)
    private PaymentStatus previousStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "next_status", nullable = false, length = 30)
    private PaymentStatus nextStatus;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", length = 40)
    private ProductType productType;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "buyer_id")
    private Long buyerId;

    @Column(name = "club_id")
    private Long clubId;

    @Enumerated(EnumType.STRING)
    @Column(name = "actor_type", nullable = false, length = 20)
    private PaymentLedgerActorType actorType;

    @Column(name = "actor_id", length = 80)
    private String actorId;

    @Column(name = "reason", length = 255)
    private String reason;

    @Column(name = "request_id", length = 80)
    private String requestId;

    @Column(name = "idempotency_key", length = 120)
    private String idempotencyKey;

    @Column(name = "metadata", columnDefinition = "TEXT")
    private String metadata;

    @Column(name = "occurred_at", nullable = false)
    private LocalDateTime occurredAt;

    @Builder
    private PaymentLedgerEntry(Long sourcePaymentId,
                               String orderId,
                               String pgTid,
                               PaymentLedgerEventType eventType,
                               PaymentStatus previousStatus,
                               PaymentStatus nextStatus,
                               ProductType productType,
                               Long amount,
                               Long buyerId,
                               Long clubId,
                               PaymentLedgerActorType actorType,
                               String actorId,
                               String reason,
                               String requestId,
                               String idempotencyKey,
                               String metadata,
                               LocalDateTime occurredAt) {
        this.sourcePaymentId = sourcePaymentId;
        this.orderId = orderId;
        this.pgTid = pgTid;
        this.eventType = eventType;
        this.previousStatus = previousStatus;
        this.nextStatus = nextStatus;
        this.productType = productType;
        this.amount = amount;
        this.buyerId = buyerId;
        this.clubId = clubId;
        this.actorType = actorType;
        this.actorId = actorId;
        this.reason = reason;
        this.requestId = requestId;
        this.idempotencyKey = idempotencyKey;
        this.metadata = metadata;
        this.occurredAt = occurredAt != null ? occurredAt : LocalDateTime.now();
    }
}
