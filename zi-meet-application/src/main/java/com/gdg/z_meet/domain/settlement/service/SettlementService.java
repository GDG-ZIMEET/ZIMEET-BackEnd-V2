package com.gdg.z_meet.domain.settlement.service;

import com.gdg.z_meet.domain.booth.entity.Club;
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

@Slf4j
@Service
@RequiredArgsConstructor
public class SettlementService {

    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final SettlementRepository settlementRepository;

    private static final double PG_FEE_RATE = 0.033; // 3.3% PG 수수료 (예시)
    private static final double PLATFORM_FEE_RATE = 0.05; // 5.0% 플랫폼 수수료 (예시)

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

            // 결제 건 정산 완료 처리 (Update is_settled = true)
            payments.forEach(p -> p.setSettled(true));
            
            log.info("Settlement created for Club: {} (Amount: {})", club.getName(), settlementAmount);
        });

        log.info("Settlement process completed.");
    }
}
