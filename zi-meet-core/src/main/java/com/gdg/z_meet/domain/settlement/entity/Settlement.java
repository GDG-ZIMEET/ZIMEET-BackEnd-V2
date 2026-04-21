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

    @Enumerated(EnumType.STRING)
    @Column(nullable = true)
    private SettlementFailureReason failureReason;

    public enum SettlementStatus {
        READY,      // 정산 데이터 생성됨
        PROCESSING, // 지급 처리 중
        PAID,       // 지급 완료
        FAILED      // 지급 실패
    }

    /** 기존 결제 로직의 에러 코드 기반 정산 실패 사유 */
    public enum SettlementFailureReason {
        KAKAO_API_ERROR("카카오 API 응답 오류"),          // KAKAO_5001
        INVALID_KAKAO_RESPONSE("잘못된 카카오 API 응답"), // KAKAO_5002
        INVALID_BUYER("결제자 정보 불일치"),              // KAKAO_4001
        PAYMENT_NOT_FOUND("결제 정보 없음"),              // PAYMENT_4004
        INVALID_ACCOUNT("계좌 정보 오류"),                // 계좌 폐쇄·오류
        SYSTEM_ERROR("시스템 오류"),                      // COMMON500
        MANUAL("수동 처리 실패");

        private final String description;

        SettlementFailureReason(String description) {
            this.description = description;
        }

        public String getDescription() {
            return description;
        }
    }

    public void markAsPaid() {
        this.status = SettlementStatus.PAID;
        this.failureReason = null;
    }

    public void markAsFailed(SettlementFailureReason reason) {
        this.status = SettlementStatus.FAILED;
        this.failureReason = reason;
    }

    /** 하위 호환 — 사유 없이 FAILED 처리 */
    public void markAsFailed() {
        markAsFailed(null);
    }
}
