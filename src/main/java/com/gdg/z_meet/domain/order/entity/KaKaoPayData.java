package com.gdg.z_meet.domain.order.entity;

import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@NoArgsConstructor
@Builder
@AllArgsConstructor
/**
 *  카카오 결제 데이터 엔티티
 */
public class KaKaoPayData extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String orderId;               // UUID

    private String tid;                   // 카카오 페이 결제 고유 번호

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType productType;      // 결제 예정인 상품 정보

    @Column(nullable = false)
    private Long totalPrice;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User buyer;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "item_id")
    private ItemPurchase itemPurchase;     // 결제 취소, 잔액 부족 등등의 사유로 여러 번 시도할 경우 고려
}
