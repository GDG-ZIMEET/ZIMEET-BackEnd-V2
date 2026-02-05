package com.gdg.z_meet.domain.order.entity;

import com.gdg.z_meet.domain.order.entity.enums.OutboxStatus;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.entity.enums.ProductType;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.global.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDateTime;

@Entity
@NoArgsConstructor
@Builder
@AllArgsConstructor
@Table(name = "kakao_pay_data", indexes = {
        @Index(name = "idx_outbox_status_payment_status", columnList = "outboxStatus, status"),
        @Index(name = "idx_recovery_status_retry", columnList = "recoveryStatus, nextRecoveryAt")
})
public class KakaoPayData extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "주문 고유 식별자 (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
    @Column(nullable = false, unique = true)
    private String orderId;

    @Schema(description = "카카오페이 결제 고유 번호 (TID)", example = "T1234567890")
    private String tid;

    @Schema(description = "결제 상태 (PREPARED: 결제 준비 완료, APPROVED: 결제 승인 완료)", example = "PREPARED")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private PaymentStatus status;

    @Schema(description = "결제 상품 유형", example = "TWO_TO_TWO")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType productType;

    @Schema(description = "총 결제 금액", example = "1000")
    @Column(nullable = false)
    private Long totalPrice;

    @Schema(description = "결제한 사용자")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User buyer;

    @Schema(description = "결제 완료 후 생성된 결제 내역")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private ItemPurchase itemPurchase;

    @Schema(description = "아웃박스 발행 상태", example = "INIT")
    @Enumerated(EnumType.STRING)
    @Builder.Default
    @Column(nullable = false)
    private OutboxStatus outboxStatus = OutboxStatus.INIT;

    @Schema(description = "메시지 발행 시 필요한 데이터 (pg_token)")
    private String pgToken;

    @Schema(description = "메시지 재시도 횟수")
    @Builder.Default
    private int publishRetryCount = 0;

    @Schema(description = "메시지 발행 완료 시각")
    private LocalDateTime publishedAt;

    // --- 보상 트랜잭션 (Recovery) 필드 합병 ---

    @Schema(description = "취소 사유")
    private String cancelReason;

    @Schema(description = "보상 트랜잭션 상태")
    @Enumerated(EnumType.STRING)
    private RecoveryStatus recoveryStatus;

    @Schema(description = "보상 트랜잭션 재시도 횟수")
    @Builder.Default
    private int recoveryRetryCount = 0;

    @Schema(description = "보상 트랜잭션 다음 재시도 시각")
    private LocalDateTime nextRecoveryAt;

    public enum RecoveryStatus {
        NONE, PENDING, PROCESSING, COMPLETED, FAILED
    }

    // Getter methods
    public Long getId() {
        return id;
    }

    public String getOrderId() {
        return orderId;
    }

    public String getTid() {
        return tid;
    }

    public PaymentStatus getStatus() {
        return status;
    }

    public ProductType getProductType() {
        return productType;
    }

    public Long getTotalPrice() {
        return totalPrice;
    }

    public User getBuyer() {
        return buyer;
    }

    public ItemPurchase getItemPurchase() {
        return itemPurchase;
    }

    // Setter methods
    public void setId(Long id) {
        this.id = id;
    }

    public void setOrderId(String orderId) {
        this.orderId = orderId;
    }

    public void setTid(String tid) {
        this.tid = tid;
    }

    public void setStatus(PaymentStatus status) {
        this.status = status;
    }

    public void setProductType(ProductType productType) {
        this.productType = productType;
    }

    public void setTotalPrice(Long totalPrice) {
        this.totalPrice = totalPrice;
    }

    public void setBuyer(User buyer) {
        this.buyer = buyer;
    }

    public void setItemPurchase(ItemPurchase itemPurchase) {
        this.itemPurchase = itemPurchase;
    }

    // Outbox methods
    public OutboxStatus getOutboxStatus() {
        return outboxStatus;
    }

    public void setOutboxStatus(OutboxStatus outboxStatus) {
        this.outboxStatus = outboxStatus;
    }

    public String getPgToken() {
        return pgToken;
    }

    public void setPgToken(String pgToken) {
        this.pgToken = pgToken;
    }

    public int getPublishRetryCount() {
        return publishRetryCount;
    }

    public void setPublishRetryCount(int publishRetryCount) {
        this.publishRetryCount = publishRetryCount;
    }

    public LocalDateTime getPublishedAt() {
        return publishedAt;
    }

    public void setPublishedAt(LocalDateTime publishedAt) {
        this.publishedAt = publishedAt;
    }

    public void markAsPublished() {
        this.outboxStatus = OutboxStatus.PUBLISHED;
        this.publishedAt = LocalDateTime.now();
    }

    public void increaseRetryCount() {
        this.publishRetryCount++;
    }

    public void markAsFailed() {
        this.outboxStatus = OutboxStatus.FAILED;
    }

    // --- Recovery Getters/Setters & Methods ---
    public String getCancelReason() {
        return cancelReason;
    }

    public void setCancelReason(String cancelReason) {
        this.cancelReason = cancelReason;
    }

    public RecoveryStatus getRecoveryStatus() {
        return recoveryStatus;
    }

    public void setRecoveryStatus(RecoveryStatus recoveryStatus) {
        this.recoveryStatus = recoveryStatus;
    }

    public int getRecoveryRetryCount() {
        return recoveryRetryCount;
    }

    public void setRecoveryRetryCount(int recoveryRetryCount) {
        this.recoveryRetryCount = recoveryRetryCount;
    }

    public LocalDateTime getNextRecoveryAt() {
        return nextRecoveryAt;
    }

    public void setNextRecoveryAt(LocalDateTime nextRecoveryAt) {
        this.nextRecoveryAt = nextRecoveryAt;
    }

    public void scheduleRecovery(String reason) {
        this.cancelReason = reason;
        this.recoveryStatus = RecoveryStatus.PENDING;
        this.recoveryRetryCount = 0;
        this.nextRecoveryAt = LocalDateTime.now();
    }

    public void markRecoveryAsProcessing() {
        this.recoveryStatus = RecoveryStatus.PROCESSING;
    }

    public void markRecoveryAsCompleted() {
        this.recoveryStatus = RecoveryStatus.COMPLETED;
    }

    public void markRecoveryAsFailed() {
        this.recoveryStatus = RecoveryStatus.FAILED;
    }

    public void increaseRecoveryRetryCount() {
        this.recoveryRetryCount++;
        this.recoveryStatus = RecoveryStatus.PENDING;
        // Exponential Backoff: 10s, 20s, 40s, 80s...
        this.nextRecoveryAt = LocalDateTime.now().plusSeconds(10L * (long) Math.pow(2, recoveryRetryCount - 1));
    }
}
