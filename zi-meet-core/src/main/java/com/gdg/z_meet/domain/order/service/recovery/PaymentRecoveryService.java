package com.gdg.z_meet.domain.order.service.recovery;

import com.gdg.z_meet.domain.order.entity.PaymentRecovery;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.repository.PaymentRecoveryRepository;
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

    private final PaymentRecoveryRepository paymentRecoveryRepository;
    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final com.gdg.z_meet.domain.order.service.cancel.KakaoPayCancelService kakaoPayCancelService;

    private static final int MAX_RETRY_COUNT = 5;

    /**
     * 보상 트랜잭션 요청 저장
     * 본 트랜잭션이 롤백되어도 이 기록은 남아야 하므로 REQUIRES_NEW 사용
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void scheduleRecovery(String orderId, String tid, String cancelReason) {
        try {
            PaymentRecovery recovery = PaymentRecovery.builder()
                    .orderId(orderId)
                    .tid(tid)
                    .cancelReason(cancelReason)
                    .build();
            paymentRecoveryRepository.save(recovery);
            log.info("보상 트랜잭션 예약 완료 - orderId: {}", orderId);
        } catch (Exception e) {
            log.error("[FATAL] 보상 트랜잭션 예약 실패 - orderId: {}", orderId, e);
        }
    }

    /**
     * 개별 보상 트랜잭션 처리
     * REQUIRES_NEW를 사용하여 각 task가 독립적으로 처리되도록 함
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void processRecovery(PaymentRecovery task) {
        try {
            // 상태를 PROCESSING으로 변경
            task.markAsProcessing();
            paymentRecoveryRepository.save(task);

            // 결제 데이터 조회
            var kakaoPayData = kakaoPayDataRepository.findByOrderId(task.getOrderId())
                    .orElseThrow(() -> new BusinessException(Code.PAYMENT_NOT_FOUND));

            // 카카오페이 취소 API 호출
            kakaoPayCancelService.compensatePayment(kakaoPayData, task.getCancelReason());

            // 성공 시 완료 처리
            task.markAsCompleted();
            paymentRecoveryRepository.save(task);
            log.info("보상 처리 성공 - recoveryId: {}", task.getId());

        } catch (Exception e) {
            log.error("보상 처리 실패 - recoveryId: {}, error: {}", task.getId(), e.getMessage(), e);

            // 실패 시 재시도 처리
            if (task.getRetryCount() >= MAX_RETRY_COUNT) {
                // 최대 재시도 초과 시 UNKNOWN 상태로 저장 (사용자 재시도 안내 필요)
                task.markAsFailed();
                
                // 결제 데이터를 UNKNOWN 상태로 변경
                try {
                    var kakaoPayDataOpt = kakaoPayDataRepository.findByOrderId(task.getOrderId());
                    if (kakaoPayDataOpt.isPresent()) {
                        var kakaoPayData = kakaoPayDataOpt.get();
                        // 이미 CANCELLED나 FAILED가 아닌 경우에만 UNKNOWN으로 변경
                        if (kakaoPayData.getStatus() != PaymentStatus.CANCELLED && 
                            kakaoPayData.getStatus() != PaymentStatus.FAILED) {
                            kakaoPayData.setStatus(PaymentStatus.UNKNOWN);
                            kakaoPayDataRepository.save(kakaoPayData);
                            log.warn("보상 처리 최종 실패로 결제 상태를 UNKNOWN으로 변경 - orderId: {}, recoveryId: {}", 
                                    task.getOrderId(), task.getId());
                        }
                    }
                } catch (Exception ex) {
                    log.error("결제 상태 UNKNOWN 변경 실패 - orderId: {}, recoveryId: {}", 
                            task.getOrderId(), task.getId(), ex);
                }
                
                log.error("보상 처리 최종 실패 (재시도 초과) - recoveryId: {}. 결제 상태는 UNKNOWN으로 저장됨. 사용자 재시도 안내 필요", 
                        task.getId());
            } else {
                task.increaseRetryCount();
                log.info("보상 처리 재시도 예약 - recoveryId: {}, retryCount: {}, next: {}",
                        task.getId(), task.getRetryCount(), task.getNextRetryAt());
            }

            paymentRecoveryRepository.save(task);
        }
    }
}
