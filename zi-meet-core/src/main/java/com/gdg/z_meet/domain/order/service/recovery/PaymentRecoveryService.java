package com.gdg.z_meet.domain.order.service.recovery;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
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
    private final com.gdg.z_meet.domain.order.service.cancel.KakaoPayCancelService kakaoPayCancelService;

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
     * 개별 보상 트랜잭션 처리
     * REQUIRES_NEW를 사용하여 각 task가 독립적으로 처리되도록 함
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processRecovery(KakaoPayData data) {
        try {
            // 상태를 PROCESSING으로 변경
            data.markRecoveryAsProcessing();
            kakaoPayDataRepository.save(data);

            // 카카오페이 취소 API 호출
            String result = kakaoPayCancelService.compensatePayment(data, data.getCancelReason());

            if ("SUCCESS".equals(result)) {
                // 성공 시 완료 처리
                data.markRecoveryAsCompleted();
                kakaoPayDataRepository.save(data);
                log.info("보상 처리 성공 - orderId: {}", data.getOrderId());
            } else if ("FATAL".equals(result)) {
                // 치명적 오류 (재시도 불가)
                data.markRecoveryAsFailed();
                // 결제 데이터 상태는 UNKNOWN이나 FAILED로 유지됨
                kakaoPayDataRepository.save(data);
                log.error("보상 처리 치명적 실패 (재시도 중단) - orderId: {}", data.getOrderId());
            } else {
                // RETRY (재시도 필요)
                throw new RuntimeException("보상 처리 실패 (재시도 필요)");
            }

        } catch (Exception e) {
            log.error("보상 처리 실패 - orderId: {}, error: {}", data.getOrderId(), e.getMessage(), e);

            // 실패 시 재시도 처리
            if (data.getRecoveryRetryCount() >= MAX_RETRY_COUNT) {
                // 최대 재시도 초과 시 FAILED 상태로 저장
                data.markRecoveryAsFailed();

                // 결제 데이터를 UNKNOWN 상태로 변경 (사용자 확인 필요)
                if (data.getStatus() != PaymentStatus.CANCELLED &&
                        data.getStatus() != PaymentStatus.FAILED) {
                    data.setStatus(PaymentStatus.UNKNOWN);
                    log.warn("보상 처리 최종 실패로 결제 상태를 UNKNOWN으로 변경 - orderId: {}", data.getOrderId());
                }

                log.error("보상 처리 최종 실패 (재시도 초과) - orderId: {}. 결제 상태는 UNKNOWN. 수동 확인 필요",
                        data.getOrderId());
            } else {
                data.increaseRecoveryRetryCount();
                log.info("보상 처리 재시도 예약 - orderId: {}, retryCount: {}, next: {}",
                        data.getOrderId(), data.getRecoveryRetryCount(), data.getNextRecoveryAt());
            }

            kakaoPayDataRepository.save(data);
        }
    }
}
