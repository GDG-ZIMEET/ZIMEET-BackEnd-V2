package com.gdg.z_meet.domain.order.ledger;

import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.entity.enums.ProductType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.time.LocalDateTime;

@Entity
@Table(
        name = "payment_current_projection",
        indexes = {
                @Index(name = "idx_payment_current_status", columnList = "current_status"),
                @Index(name = "idx_payment_current_settlement", columnList = "current_status, settled, club_id"),
                @Index(name = "idx_payment_current_latest_ledger", columnList = "latest_ledger_id")
        }
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
public class PaymentCurrentProjection {

    @Id
    @Column(name = "order_id", length = 80)
    private String orderId;

    @Column(name = "source_payment_id")
    private Long sourcePaymentId;

    @Enumerated(EnumType.STRING)
    @Column(name = "current_status", nullable = false, length = 30)
    private PaymentStatus currentStatus;

    @Column(name = "latest_ledger_id", nullable = false)
    private Long latestLedgerId;

    @Column(name = "pg_tid", length = 80)
    private String pgTid;

    @Enumerated(EnumType.STRING)
    @Column(name = "product_type", length = 40)
    private ProductType productType;

    @Column(name = "amount")
    private Long amount;

    @Column(name = "buyer_id")
    private Long buyerId;

    @Column(name = "club_id")
    private Long clubId;

    @Column(name = "settled", nullable = false)
    private boolean settled;

    @Column(name = "last_event_type", nullable = false, length = 40)
    @Enumerated(EnumType.STRING)
    private PaymentLedgerEventType lastEventType;

    @Column(name = "updated_from_ledger_at", nullable = false)
    private LocalDateTime updatedFromLedgerAt;

    public PaymentCurrentProjection(String orderId) {
        this.orderId = orderId;
    }

    public void apply(PaymentLedgerEntry entry) {
        if (latestLedgerId != null && latestLedgerId >= entry.getLedgerId()) {
            return;
        }
        this.sourcePaymentId = entry.getSourcePaymentId();
        this.currentStatus = entry.getNextStatus();
        this.latestLedgerId = entry.getLedgerId();
        this.pgTid = entry.getPgTid();
        this.productType = entry.getProductType();
        this.amount = entry.getAmount();
        this.buyerId = entry.getBuyerId();
        this.clubId = entry.getClubId();
        this.settled = false;
        this.lastEventType = entry.getEventType();
        this.updatedFromLedgerAt = LocalDateTime.now();
    }
}
