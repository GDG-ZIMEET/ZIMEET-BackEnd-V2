package com.gdg.z_meet.domain.order.entity;

import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.order.entity.enums.ProductType;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.global.common.BaseEntity;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ItemPurchase extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Schema(description = "주문 고유 식별자 (UUID)", example = "550e8400-e29b-41d4-a716-446655440000")
    @Column(nullable = false, unique = true)
    private String orderId;

    @Schema(description = "카카오페이 결제 고유 번호 (TID)", example = "T1234567890")
    private String tid;

    @Schema(description = "결제 상품 유형 (TWO_TO_TWO, THREE_TO_THREE, TICKET, SEASON)", example = "TWO_TO_TWO")
    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private ProductType productType;

    @Schema(description = "단건 결제 금액", example = "1000")
    @Column(nullable = false)
    private Long totalPrice;

    @Schema(description = "부가세 금액", example = "100")
    private Long vat;

    @Schema(description = "결제한 사용자")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id", nullable = false)
    private User buyer;

    @Schema(description = "hi 상품인 경우 연결되는 팀")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "team_id")
    private Team team;

    @Schema(description = "티켓 상품인 경우 연결되는 사용자 프로필")
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_profile_id")
    private UserProfile userProfile;
}
