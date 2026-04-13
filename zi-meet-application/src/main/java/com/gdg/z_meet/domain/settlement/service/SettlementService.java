package com.gdg.z_meet.domain.settlement.service;

import com.gdg.z_meet.domain.booth.entity.Club;
import com.gdg.z_meet.domain.booth.repository.ClubRepository;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.domain.settlement.entity.Settlement;
import com.gdg.z_meet.domain.settlement.repository.SettlementRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final SettlementRepository settlementRepository;
    private final ClubRepository clubRepository;

    private static final double PG_FEE_RATE = 0.033; // 3.3% PG 수수료 (예시)
    private static final double PLATFORM_FEE_RATE = 0.05; // 5.0% 플랫폼 수수료 (예시)

    // ── 부스별 정산 현황 요약 ──────────────────────────────────────
    @Transactional(readOnly = true)
    public List<ClubSettlementSummary> getClubSettlementSummaries() {
        return clubRepository.findAll(org.springframework.data.domain.Sort.by("name"))
                .stream()
                .map(club -> {
                    long count = kakaoPayDataRepository.countByClubIdAndStatusAndIsSettledFalse(
                            club.getId(), PaymentStatus.APPROVED);
                    long amount = kakaoPayDataRepository.sumTotalPriceByClubIdAndStatusAndIsSettledFalse(
                            club.getId(), PaymentStatus.APPROVED);
                    Settlement last = settlementRepository
                            .findTopByClubIdOrderBySettlementDateDesc(club.getId()).orElse(null);
                    return new ClubSettlementSummary(club, count, amount, last);
                })
                .toList();
    }

    // ── 특정 부스만 정산 실행 ─────────────────────────────────────
    @Transactional
    public void processSettlementForClub(Long clubId) {
        List<KakaoPayData> payments = kakaoPayDataRepository
                .findByClubIdAndStatusAndIsSettledFalse(clubId, PaymentStatus.APPROVED);
        if (payments.isEmpty()) {
            throw new IllegalStateException("미정산 결제가 없습니다.");
        }

        Club club = payments.get(0).getClub();
        long totalAmount = payments.stream().mapToLong(KakaoPayData::getTotalPrice).sum();
        long pgFee = (long) (totalAmount * PG_FEE_RATE);
        long platformFee = (long) (totalAmount * PLATFORM_FEE_RATE);

        Settlement settlement = Settlement.builder()
                .club(club)
                .totalAmount(totalAmount)
                .feeAmount(pgFee + platformFee)
                .settlementAmount(totalAmount - pgFee - platformFee)
                .status(Settlement.SettlementStatus.READY)
                .settlementDate(java.time.LocalDate.now())
                .bank(club.getBank())
                .account(club.getAccount())
                .build();
        settlementRepository.save(settlement);

        payments.forEach(p -> {
            p.setSettled(true);
            p.setSettlement(settlement);
        });
        log.info("Settlement created for Club: {} (Amount: {})", club.getName(), settlement.getSettlementAmount());
    }

    @Transactional
    public void processDelayedSettlement() {
        log.info("Starting delayed settlement process...");

        // 1. 정산되지 않은 성공 결제 건 조회 (소급 정산의 핵심: isSettled = false)
        List<KakaoPayData> unsettledPayments = kakaoPayDataRepository.findByStatusAndIsSettledFalse(PaymentStatus.APPROVED);

        if (unsettledPayments.isEmpty()) {
            log.info("No unsettled payments found.");
            return;
        }

        log.info("Found {} unsettled payments.", unsettledPayments.size());

        // 2. 부스(Club)별로 그룹화
        Map<Club, List<KakaoPayData>> paymentsByClub = unsettledPayments.stream()
                .filter(p -> p.getClub() != null)
                .collect(Collectors.groupingBy(KakaoPayData::getClub));

        LocalDate settlementDate = LocalDate.now();

        // 3. 부스별 정산 처리
        paymentsByClub.forEach((club, payments) -> {
            long totalAmount = payments.stream().mapToLong(KakaoPayData::getTotalPrice).sum();
            long pgFee = (long) (totalAmount * PG_FEE_RATE);
            long platformFee = (long) (totalAmount * PLATFORM_FEE_RATE);
            long settlementAmount = totalAmount - pgFee - platformFee;

            // 정산 엔티티 생성 및 저장
            Settlement settlement = Settlement.builder()
                    .club(club)
                    .totalAmount(totalAmount)
                    .feeAmount(pgFee + platformFee)
                    .settlementAmount(settlementAmount)
                    .status(Settlement.SettlementStatus.READY)
                    .settlementDate(settlementDate)
                    .bank(club.getBank())
                    .account(club.getAccount())
                    .build();

            settlementRepository.save(settlement);

            // 결제 건 정산 완료 처리 (Update is_settled = true) 및 연관관계 매핑
            payments.forEach(p -> {
                p.setSettled(true);
                p.setSettlement(settlement);
            });
            
            log.info("Settlement created for Club: {} (Amount: {})", club.getName(), settlementAmount);
        });

        log.info("Settlement process completed.");
    }

    @Transactional(readOnly = true)
    public Page<Settlement> getSettlements(
            Settlement.SettlementStatus status,
            Long clubId,
            LocalDate startDate,
            LocalDate endDate,
            Pageable pageable) {
        
        if (status != null) {
            return settlementRepository.findByStatus(status, pageable);
        }
        if (clubId != null) {
            return settlementRepository.findByClubId(clubId, pageable);
        }
        if (startDate != null && endDate != null) {
            return settlementRepository.findBySettlementDateBetween(startDate, endDate, pageable);
        }
        
        return settlementRepository.findAll(pageable);
    }

    @Transactional(readOnly = true)
    public Settlement getSettlementDetail(Long settlementId) {
        return settlementRepository.findById(settlementId)
                .orElseThrow(() -> new IllegalArgumentException("Settlement not found"));
    }

    @Transactional
    public void updateSettlementStatus(Long settlementId, Settlement.SettlementStatus newStatus,
                                       Settlement.SettlementFailureReason reason) {
        Settlement settlement = getSettlementDetail(settlementId);
        if (newStatus == Settlement.SettlementStatus.PAID) {
            settlement.markAsPaid();
        } else if (newStatus == Settlement.SettlementStatus.FAILED) {
            settlement.markAsFailed(reason);
        } else {
            throw new IllegalArgumentException("Unsupported status transition");
        }
    }

    @Transactional(readOnly = true)
    public List<KakaoPayData> getPaymentsBySettlement(Long settlementId) {
        return kakaoPayDataRepository.findBySettlementId(settlementId);
    }

    @Transactional(readOnly = true)
    public SettlementStats getStats() {
        long readyCount       = settlementRepository.countByStatus(Settlement.SettlementStatus.READY);
        long processingCount  = settlementRepository.countByStatus(Settlement.SettlementStatus.PROCESSING);
        long paidCount        = settlementRepository.countByStatus(Settlement.SettlementStatus.PAID);
        long failedCount      = settlementRepository.countByStatus(Settlement.SettlementStatus.FAILED);
        long totalPaidAmount  = settlementRepository.sumSettlementAmountByStatus(Settlement.SettlementStatus.PAID);
        long totalPendingAmount = settlementRepository.sumSettlementAmountByStatus(Settlement.SettlementStatus.READY)
                + settlementRepository.sumSettlementAmountByStatus(Settlement.SettlementStatus.PROCESSING);
        long totalPaymentCount   = kakaoPayDataRepository.countByStatus(PaymentStatus.APPROVED);
        long unsettledPaymentCount = kakaoPayDataRepository.countByStatusAndIsSettledFalse(PaymentStatus.APPROVED);
        return new SettlementStats(readyCount, processingCount, paidCount, failedCount,
                totalPaidAmount, totalPendingAmount, totalPaymentCount, unsettledPaymentCount);
    }

    public record ClubSettlementSummary(
            Club club,
            long unsettledCount,
            long unsettledAmount,
            Settlement lastSettlement   // nullable
    ) {}

    public record SettlementStats(
            long readyCount,
            long processingCount,
            long paidCount,
            long failedCount,
            long totalPaidAmount,
            long totalPendingAmount,
            long totalPaymentCount,       // 전체 결제 건수 (APPROVED)
            long unsettledPaymentCount    // 미정산 결제 건수
    ) {}
}
