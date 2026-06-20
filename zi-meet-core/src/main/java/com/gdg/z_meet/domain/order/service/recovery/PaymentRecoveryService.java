package com.gdg.z_meet.domain.order.service.recovery;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.domain.order.service.cancel.KakaoPayCancelService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;


@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentRecoveryService {

    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final KakaoPayCancelService kakaoPayCancelService;
    private final PaymentRecoveryTransactionService recoveryTransactionService;

    private static final int MAX_RETRY_COUNT = 3;

    /**
     * 보상 트랜잭션 예약
     * 본 트랜잭션이 롤백되어도 이 기록은 남아야 하므로 REQUIRES_NEW 사용
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleRecovery(String orderId, String tid, String cancelReason) {
        try {
            kakaoPayDataRepository.findByOrderId(orderId).ifPresent(data -> {
                data.scheduleRecovery(cancelReason);
                kakaoPayDataRepository.save(data);
                log.info("보상 트랜잭션 예약 완료 - orderId: {}", orderId);
            });
        } catch (Exception e) {
            log.error("[FATAL] 보상 트랜잭션 예약 실패 - orderId: {}", orderId, e);
        }
    }

    /**
     * 선점, 외부 PG 호출, 결과 저장을 각각 분리해 DB 락을 잡은 채 외부 호출하지 않는다.
     */
    public void processRecovery(Long paymentId) {
        if (!recoveryTransactionService.claim(paymentId)) {
            log.debug("이미 다른 워커가 선점한 보상 작업 - paymentId: {}", paymentId);
            return;
        }

        KakaoPayData data = recoveryTransactionService.find(paymentId);
        try {
            // 카카오페이 취소 API 호출
            String result = kakaoPayCancelService.compensatePayment(data, data.getCancelReason());

            if ("SUCCESS".equals(result)) {
                // 성공 시 완료 처리
                recoveryTransactionService.complete(paymentId);
                log.info("보상 처리 성공 - orderId: {}", data.getOrderId());
            } else if ("FATAL".equals(result)) {
                // 치명적 오류 (재시도 불가)
                recoveryTransactionService.fail(paymentId);
                log.error("보상 처리 치명적 실패 (재시도 중단) - orderId: {}", data.getOrderId());
            } else {
                // RETRY (재시도 필요)
                throw new RuntimeException("보상 처리 실패 (재시도 필요)");
            }

        } catch (Exception e) {
            log.error("보상 처리 실패 - orderId: {}, error: {}", data.getOrderId(), e.getMessage(), e);

            // 실패 시 재시도 처리
            if (recoveryTransactionService.rescheduleOrExhaust(paymentId, MAX_RETRY_COUNT)) {
                log.error("보상 처리 최종 실패 (재시도 초과) - orderId: {}. 결제 상태는 UNKNOWN. 수동 확인 필요",
                        data.getOrderId());
            } else {
                KakaoPayData rescheduled = recoveryTransactionService.find(paymentId);
                log.info("보상 처리 재시도 예약 - orderId: {}, retryCount: {}, next: {}",
                        rescheduled.getOrderId(), rescheduled.getRecoveryRetryCount(), rescheduled.getNextRecoveryAt());
            }
        }
    }
}
