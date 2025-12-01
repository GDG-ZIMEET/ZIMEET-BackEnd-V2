package com.gdg.z_meet.domain.order.entity;

import jakarta.persistence.*;
import lombok.AccessLevel;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import org.springframework.data.annotation.CreatedDate;
import org.springframework.data.annotation.LastModifiedDate;
import org.springframework.data.jpa.domain.support.AuditingEntityListener;

import java.time.LocalDateTime;

@Entity
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@EntityListeners(AuditingEntityListener.class)
@Table(name = "payment_recovery", indexes = {
        @Index(name = "idx_status_retry", columnList = "status, next_retry_at")
})
public class PaymentRecovery {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String orderId;

    private String tid;

    private String cancelReason;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private RecoveryStatus status;

    @Column(nullable = false)
    private int retryCount;

    private LocalDateTime nextRetryAt;

    @CreatedDate
    private LocalDateTime createdAt;

    @LastModifiedDate
    private LocalDateTime updatedAt;

    public enum RecoveryStatus {
        PENDING, PROCESSING, COMPLETED, FAILED
    }

    @Builder
    public PaymentRecovery(String orderId, String tid, String cancelReason) {
        this.orderId = orderId;
        this.tid = tid;
        this.cancelReason = cancelReason;
        this.status = RecoveryStatus.PENDING;
        this.retryCount = 0;
        this.nextRetryAt = LocalDateTime.now();
    }

    public void markAsProcessing() {
        this.status = RecoveryStatus.PROCESSING;
    }

    public void markAsCompleted() {
        this.status = RecoveryStatus.COMPLETED;
    }

    public void markAsFailed() {
        this.status = RecoveryStatus.FAILED;
    }

    public void increaseRetryCount() {
        this.retryCount++;
        this.status = RecoveryStatus.PENDING;
        // Exponential Backoff: 10s, 20s, 40s, 80s...
        this.nextRetryAt = LocalDateTime.now().plusSeconds(10L * (long) Math.pow(2, retryCount - 1));
    }
}
