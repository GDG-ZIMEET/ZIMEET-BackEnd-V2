package com.gdg.z_meet.domain.order.service.sync;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.ledger.PaymentLedgerActorType;
import com.gdg.z_meet.domain.order.ledger.PaymentLedgerRecorder;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.domain.order.service.approve.KakaoPayApproveTransactionService;
import com.gdg.z_meet.domain.order.service.cancel.KakaoPayCancelService;
import com.gdg.z_meet.domain.order.service.locking.KakaoPayLockService;
import com.gdg.z_meet.domain.order.service.recovery.PaymentRecoveryService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;
import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSyncService {

    private final KaKaoPayApiClient kaKaoPayApiClient;
    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final KakaoPayApproveTransactionService approveTransactionService;
    private final KakaoPayCancelService kakaoPayCancelService;
    private final PaymentLedgerRecorder paymentLedgerRecorder;
    private final KakaoPayLockService lockService;
    private final PaymentRecoveryService recoveryService;

    /**
     * 외부 PG사와 상태를 동기화하여 결제 상태를 확정함
     * 
     * @param orderId 주문 ID
     * @return 동기화 결과 (성공 시 true)
     */
    @Transactional
    public boolean syncPaymentStatus(String orderId) {
        return lockService.executeWithLock(orderId, () -> syncPaymentStatusLocked(orderId));
    }

    private boolean syncPaymentStatusLocked(String orderId) {
        log.info("결제 상태 동기화 시작 - orderId: {}", orderId);

        KakaoPayData kakaoPayData = kakaoPayDataRepository.findByOrderId(orderId)
                .orElse(null);

        if (kakaoPayData == null) {
            log.warn("동기화 대상을 찾을 수 없음 - orderId: {}", orderId);
            return false;
        }

        // 이미 완료된 건은 스킵
        if (kakaoPayData.getStatus() == PaymentStatus.APPROVED ||
                kakaoPayData.getStatus() == PaymentStatus.CANCELLED) {
            log.info("이미 확정된 결제입니다 - orderId: {}, status: {}", orderId, kakaoPayData.getStatus());
            return true;
        }

        if (kakaoPayData.getTid() == null || kakaoPayData.getTid().isEmpty()) {
            log.warn("TID가 없어 동기화 불가 - orderId: {}", orderId);
            return false;
        }

        // 1. 카카오페이 상태 조회
        Optional<KaKaoPayApproveDTO.KaKaoApiResponse> inquiryResult = kaKaoPayApiClient
                .inquirePaymentStatus(kakaoPayData.getTid());

        if (inquiryResult.isPresent()) {
            KaKaoPayApproveDTO.KaKaoApiResponse response = inquiryResult.get();
            String pgStatus = response.getStatus();
            log.info("카카오페이 조회 결과 - orderId: {}, pgStatus: {}", orderId, pgStatus);

            switch (pgStatus) {
                case "SUCCESS_PAYMENT":
                    // 실제로는 결제 성공인데 우리 DB만 미완료인 경우 -> 승인 처리
                    log.info("결제 성공 확인됨. 승인 처리 진행 - orderId: {}", orderId);
                    approveTransactionService.completePayment(kakaoPayData.getId(), response, null);
                    return true;

                case "READY":
                    // 5분 이상 READY 상태라면 결제가 중단된 것으로 간주 -> 강제 취소 및 정리
                    log.warn("READY 상태 낙오 데이터 발견. 강제 정리 시작 - orderId: {}", orderId);
                    String compensationResult = kakaoPayCancelService.compensatePaymentUnderExistingLock(
                            kakaoPayData, "GHOST_READY_PAYMENT_CLEANUP");
                    if ("RETRY".equals(compensationResult)) {
                        recoveryService.scheduleRecovery(
                                kakaoPayData.getOrderId(), kakaoPayData.getTid(), "GHOST_READY_PAYMENT_CLEANUP");
                        return false;
                    }
                    return true;

                case "CANCEL_PAYMENT":
                case "PART_CANCEL_PAYMENT":
                case "FAIL_PAYMENT":
                    log.info("결제 취소/실패 확인됨. 상태 업데이트 - orderId: {}, pgStatus: {}", orderId, pgStatus);
                    PaymentStatus previousStatus = kakaoPayData.getStatus();
                    PaymentStatus nextStatus = pgStatus.contains("CANCEL") ? PaymentStatus.CANCELLED : PaymentStatus.FAILED;
                    kakaoPayData.setStatus(nextStatus);
                    kakaoPayDataRepository.save(kakaoPayData);
                    paymentLedgerRecorder.record(
                            kakaoPayData,
                            previousStatus,
                            nextStatus,
                            PaymentLedgerActorType.BATCH,
                            "payment-sync",
                            "Payment status synchronized from PG status: " + pgStatus,
                            "sync-" + orderId + "-" + nextStatus,
                            "source=kakao_pay_sync"
                    );
                    return true;

                default:
                    log.warn("알 수 없는 PG 상태 - orderId: {}, pgStatus: {}", orderId, pgStatus);
                    // PG 상태를 확인하지 못한 타임아웃 건은 FAILED로 단정하지 않고 수동 정합성 대상으로 남긴다.
                    if (kakaoPayData.getCreatedAt().isBefore(LocalDateTime.now().minusMinutes(30))) {
                        log.error("[ALARM] 30분이 지난 UNKNOWN 결제 발견. UNKNOWN 유지 및 수동 확인 필요 - orderId: {}",
                                orderId);
                    }
                    return false;
            }
        }

        log.warn("PG사 상태 조회 실패 - orderId: {}", orderId);
        return false;
    }
}
