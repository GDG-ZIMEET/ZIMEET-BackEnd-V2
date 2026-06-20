package com.gdg.z_meet.domain.order.ledger;

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
        name = "payment_projection_outbox",
        indexes = {
                @Index(name = "idx_payment_projection_outbox_status_id", columnList = "status, outbox_id"),
                @Index(name = "uk_payment_projection_outbox_ledger", columnList = "ledger_id", unique = true)
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentProjectionOutbox {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "outbox_id")
    private Long outboxId;

    @Column(name = "ledger_id", nullable = false)
    private Long ledgerId;

    @Column(name = "order_id", nullable = false, length = 80)
    private String orderId;

    @Enumerated(EnumType.STRING)
    @Column(name = "status", nullable = false, length = 20)
    private ProjectionOutboxStatus status;

    @Column(name = "retry_count", nullable = false)
    private int retryCount;

    @Column(name = "created_at", nullable = false)
    private LocalDateTime createdAt;

    @Column(name = "processed_at")
    private LocalDateTime processedAt;

    @Column(name = "last_error", length = 500)
    private String lastError;

    @Builder
    private PaymentProjectionOutbox(Long ledgerId, String orderId) {
        this.ledgerId = ledgerId;
        this.orderId = orderId;
        this.status = ProjectionOutboxStatus.PENDING;
        this.retryCount = 0;
        this.createdAt = LocalDateTime.now();
    }

    public void markProcessed() {
        this.status = ProjectionOutboxStatus.PROCESSED;
        this.processedAt = LocalDateTime.now();
        this.lastError = null;
    }

    public void markFailed(String message) {
        this.status = ProjectionOutboxStatus.FAILED;
        this.retryCount++;
        this.lastError = message != null && message.length() > 500 ? message.substring(0, 500) : message;
    }

    public void reopen() {
        this.status = ProjectionOutboxStatus.PENDING;
    }
}
