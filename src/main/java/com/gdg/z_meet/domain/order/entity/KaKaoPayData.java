package com.gdg.z_meet.domain.order.entity;

import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.global.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Setter
@NoArgsConstructor
@Builder
@AllArgsConstructor
public class KaKaoPayData extends BaseEntity {

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
}
