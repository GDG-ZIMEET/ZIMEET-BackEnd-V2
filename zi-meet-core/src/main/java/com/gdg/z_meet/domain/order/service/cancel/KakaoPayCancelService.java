package com.gdg.z_meet.domain.order.service.cancel;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.converter.KaKaoPayCancelConverter;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.dto.KaKaoPayCancelDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import com.gdg.z_meet.domain.order.service.idempotency.KakaoPayIdempotencyService;
import com.gdg.z_meet.domain.order.service.locking.KakaoPayLockService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPayCancelService {

    private final KaKaoPayApiClient kaKaoPayApiClient;
    private final KakaoPayCancelTransactionService transactionService;
    private final KakaoPayLockService lockService;
    private final KakaoPayIdempotencyService idempotencyService;

    /**
     * 결제 취소 처리
     * 
     * @param parameter 취소 파라미터
     * @param userId    사용자 ID
     * @return 취소 응답
     */
    public KaKaoPayCancelDTO.Response cancel(KaKaoPayCancelDTO.Parameter parameter, Long userId,
                                             String idempotencyKey) {
        String key = (idempotencyKey == null || idempotencyKey.isBlank())
                ? null
                : userId + ":CANCEL:" + idempotencyKey;
        String payload = parameter.getOrderId() + ":" + String.valueOf(parameter.getCancelReason());
        var validation = idempotencyService.validate(key, payload);
        if (validation.isCached()) {
            return (KaKaoPayCancelDTO.Response) validation.getCachedResponse();
        }

        try {
            KaKaoPayCancelDTO.Response response = lockService.executeWithLock(
                    parameter.getOrderId(), () -> cancelLocked(parameter, userId));
            idempotencyService.cacheResponse(key, response);
            return response;
        } finally {
            idempotencyService.unmarkAsProcessing(key, validation.getProcessingToken());
        }
    }

    private KaKaoPayCancelDTO.Response cancelLocked(KaKaoPayCancelDTO.Parameter parameter, Long userId) {
        KakaoPayData current = transactionService.findPaymentData(parameter.getOrderId());
        if (!current.getBuyer().getId().equals(userId)) {
            throw new BusinessException(Code.KAKAO_API_INVALID_BUYER);
        }
        if (current.getStatus() == PaymentStatus.CANCELLED) {
            return cancelledResponse(current.getOrderId(), null);
        }

        // 1. 결제 정보 조회 및 검증 (Tx 1)
        KakaoPayData kakaoPayData = transactionService.validateAndGetPaymentData(parameter.getOrderId(), userId);

        // 취소 파라미터에 TID와 금액 정보 추가
        parameter = KaKaoPayCancelConverter.toParameter(kakaoPayData, parameter.getCancelReason());

        // 2. 카카오페이 취소 API 호출 (No Tx)
        Optional<KaKaoPayCancelDTO.KakaoApiResponse> cancelResult;
        try {
            cancelResult = kaKaoPayApiClient.requestPaymentCancel(parameter);
        } catch (RuntimeException e) {
            cancelResult = Optional.empty();
        }
        KaKaoPayCancelDTO.KakaoApiResponse kakaoApiResponse = cancelResult
                .orElseGet(() -> resolveUncertainCancel(kakaoPayData));

        log.debug("카카오페이 결제 취소 성공 - orderId: {}", parameter.getOrderId());

        // 3. 결제 상태 업데이트 (Tx 2)
        transactionService.completeCancel(kakaoPayData.getId());

        return KaKaoPayCancelConverter.toResponse(kakaoApiResponse, parameter.getOrderId());
    }

    private KaKaoPayCancelDTO.KakaoApiResponse resolveUncertainCancel(KakaoPayData data) {
        Optional<KaKaoPayApproveDTO.KaKaoApiResponse> inquiry = kaKaoPayApiClient.inquirePaymentStatus(data.getTid());
        if (inquiry.isPresent() && ("CANCEL_PAYMENT".equals(inquiry.get().getStatus()) ||
                "PART_CANCEL_PAYMENT".equals(inquiry.get().getStatus()))) {
            return KaKaoPayCancelDTO.KakaoApiResponse.builder()
                    .status(inquiry.get().getStatus())
                    .build();
        }
        transactionService.scheduleCancelRecovery(data.getId(), "CANCEL_TIMEOUT_UNKNOWN");
        throw new BusinessException(Code.PAYMENT_UNKNOWN_STATUS_RETRY);
    }

    private KaKaoPayCancelDTO.Response cancelledResponse(String orderId, String cancelledAt) {
        return KaKaoPayCancelDTO.Response.builder()
                .orderId(orderId)
                .canceledAt(cancelledAt)
                .status("CANCEL_PAYMENT")
                .build();
    }

    /**
     * 내부 처리 실패로 인한 자동 취소 (보상 트랜잭션)
     * 
     * 무한 루프 방지:
     * 1. 타임아웃 발생 시 취소 상태 조회 시도
     * 2. 조회 실패 시 UNKNOWN 상태로 저장 (재시도 안내 필요)
     * 3. 이미 UNKNOWN 상태인 경우 재시도 처리
     * 
     * @param kakaoPayData 결제 데이터
     * @param cancelReason 취소 사유
     */
    /**
     * 보상 트랜잭션 핵심 로직
     * 1. PG사 현재 상태 조회
     * 2. 상태에 따른 후속 조치 (성공 시 DB 보정, 실패/미결제 시 취소 또는 종료)
     */
    public String compensatePayment(KakaoPayData kakaoPayData, String cancelReason) {
        return lockService.executeWithLock(kakaoPayData.getOrderId(),
                () -> compensatePaymentLocked(kakaoPayData.getId(), cancelReason));
    }

    /** 동일 orderId 락을 이미 보유한 Sync 흐름에서만 사용한다. */
    public String compensatePaymentUnderExistingLock(KakaoPayData kakaoPayData, String cancelReason) {
        return compensatePaymentLocked(kakaoPayData.getId(), cancelReason);
    }

    private String compensatePaymentLocked(Long paymentId, String cancelReason) {
        KakaoPayData kakaoPayData = transactionService.findPaymentDataById(paymentId);
        log.warn("보상 트랜잭션 수행 - orderId: {}, 사유: {}", kakaoPayData.getOrderId(), cancelReason);

        try {
            // 1. PG사 상태 조회 (가장 확실한 정보원)
            if (kakaoPayData.getTid() == null || kakaoPayData.getTid().isEmpty()) {
                log.warn("TID 누락 - 보상 불가 (FATAL): {}", kakaoPayData.getOrderId());
                transactionService.markAsFailed(kakaoPayData.getId());
                return "FATAL";
            }

            Optional<KaKaoPayApproveDTO.KaKaoApiResponse> inquiryOpt = kaKaoPayApiClient.inquirePaymentStatus(kakaoPayData.getTid());
            
            if (inquiryOpt.isEmpty()) {
                log.warn("PG사 응답 없음 (RETRY) - orderId: {}", kakaoPayData.getOrderId());
                return "RETRY";
            }

            String pgStatus = inquiryOpt.get().getStatus();
            log.info("PG사 상태 확인 결과: {} - orderId: {}", pgStatus, kakaoPayData.getOrderId());

            // 2. 상태별 분기 처리
            switch (pgStatus) {
                case "SUCCESS_PAYMENT":
                    // PG는 성공인데 우리만 모르는 경우 -> 결제 완료 처리 시도 (또는 정책에 따라 취소)
                    log.info("PG 성공 확인 -> 누락된 결제 완료 처리 진행: {}", kakaoPayData.getOrderId());
                    // 실제 구현 시에는 completePayment와 유사한 보정 로직 호출
                    // 여기서는 안전하게 취소(Cancel)를 수행하거나 상태만 업데이트 할 수 있음
                    // 결제 정합성을 위해 여기서는 '이미 결제됨' 로그 후 정리
                    return performCancel(kakaoPayData, "SYSTEM_COMPENSATION_AFTER_SUCCESS");

                case "CANCEL_PAYMENT":
                case "PART_CANCEL_PAYMENT":
                    log.info("이미 취소된 결제 확인 - orderId: {}", kakaoPayData.getOrderId());
                    transactionService.completeCancel(kakaoPayData.getId());
                    return "SUCCESS";

                case "FAIL_PAYMENT":
                    log.info("PG사 실패 확인 - orderId: {}", kakaoPayData.getOrderId());
                    transactionService.markAsFailed(kakaoPayData.getId());
                    return "SUCCESS";

                default:
                    // 결제 전 단계이거나 알 수 없는 경우 -> 안전하게 취소 시도
                    return performCancel(kakaoPayData, cancelReason);
            }

        } catch (Exception e) {
            log.error("보상 트랜잭션 오류 (RETRY) - orderId: {}, error: {}", kakaoPayData.getOrderId(), e.getMessage());
            return "RETRY";
        }
    }

    private String performCancel(KakaoPayData kakaoPayData, String reason) {
        KaKaoPayCancelDTO.Parameter params = KaKaoPayCancelConverter.toParameter(kakaoPayData, reason);
        Optional<KaKaoPayCancelDTO.KakaoApiResponse> res = kaKaoPayApiClient.requestPaymentCancel(params);
        
        if (res.isPresent()) {
            transactionService.completeCancel(kakaoPayData.getId());
            return "SUCCESS";
        }
        return "RETRY";
    }
}
