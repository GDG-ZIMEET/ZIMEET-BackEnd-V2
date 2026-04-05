package com.gdg.z_meet.domain.settlement.entity;

import com.gdg.z_meet.domain.booth.entity.Club;
import com.gdg.z_meet.domain.order.entity.enums.Bank;
import com.gdg.z_meet.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;
import java.time.LocalDate;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Settlement extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "club_id")
    private Club club;

    @Column(nullable = false)
    private Long totalAmount;

    @Column(nullable = false)
    private Long feeAmount;

    @Column(nullable = false)
    private Long settlementAmount;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private SettlementStatus status;

    @Column(nullable = false)
    private LocalDate settlementDate;

    // 정산 시점의 계좌 정보를 스냅샷으로 저장 (향후 부스 정보가 바뀌어도 기록 보존)
    @Enumerated(EnumType.STRING)
    private Bank bank;

    private String account;

    public enum SettlementStatus {
        READY,      // 정산 데이터 생성됨
        PROCESSING, // 지급 처리 중
        PAID,       // 지급 완료
        FAILED      // 지급 실패
    }

    public void markAsPaid() {
        this.status = SettlementStatus.PAID;
    }

    public void markAsFailed() {
        this.status = SettlementStatus.FAILED;
    }
}
