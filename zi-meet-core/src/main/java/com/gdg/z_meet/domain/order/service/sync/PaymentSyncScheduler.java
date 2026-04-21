package com.gdg.z_meet.domain.order.service.sync;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("worker")
public class PaymentSyncScheduler {

    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final PaymentSyncService paymentSyncService;

    /**
     * UNKNOWN 상태인 결제건에 대해 PG사 상태를 조회하여 동기화
     * 1분마다 실행
     */
    @Scheduled(fixedDelay = 60000)
    public void syncUnknownPayments() {
        // 생성된 지 1분이 경과한 UNKNOWN 상태의 결제건 조회
        LocalDateTime cutoff = LocalDateTime.now().minusMinutes(1);
        List<KakaoPayData> unknownPayments = kakaoPayDataRepository.findByStatusAndCreatedAtBefore(
                PaymentStatus.UNKNOWN, cutoff);

        if (unknownPayments.isEmpty()) {
            return;
        }

        log.info("UNKNOWN 상태 결제 {}건 발견. 동기화 시작", unknownPayments.size());

        for (KakaoPayData payment : unknownPayments) {
            try {
                paymentSyncService.syncPaymentStatus(payment.getOrderId());
            } catch (Exception e) {
                log.error("UNKNOWN 결제 동기화 중 에러 - orderId: {}, error: {}",
                        payment.getOrderId(), e.getMessage());
            }
        }
    }
}
