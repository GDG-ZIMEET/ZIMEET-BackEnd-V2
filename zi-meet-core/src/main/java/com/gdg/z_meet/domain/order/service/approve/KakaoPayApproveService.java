package com.gdg.z_meet.domain.order.service.approve;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.repository.KakaoItemPurchaseRepository;

import com.gdg.z_meet.domain.order.service.idempotency.KakaoPayIdempotencyService;
import com.gdg.z_meet.domain.order.service.locking.KakaoPayLockService;
// import com.gdg.z_meet.domain.order.service.PaymentCompensationProducer;
// import com.gdg.z_meet.domain.order.service.cancel.KakaoPayCancelService;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPayApproveService {

    private final KaKaoPayApiClient kaKaoPayApiClient;
    private final KakaoItemPurchaseRepository itemPurchaseRepository;
    private final KakaoPayIdempotencyService kakaoPayIdempotencyService;
    private final KakaoPayLockService kakaoPayLockService;
    private final KakaoPayApproveTransactionService transactionService;
    private final com.gdg.z_meet.domain.order.service.recovery.PaymentRecoveryService paymentRecoveryService;

    /**
     * Try-Confirm-Cancel 패턴 적용
     * 1. Try: 결제 시도 (상태 변경)
     * 2. Confirm: 외부 API 호출 및 완료 처리
     * 3. Cancel: 실패 시 보상 트랜잭션
     */
    public KaKaoPayApproveDTO.Response approve(KaKaoPayApproveDTO.Parameter parameter, String idempotencyKey) {
        // 멱등성 키 네임스페이스: userId:idempotencyKey
        final String namespacedKey = (idempotencyKey == null || idempotencyKey.isEmpty())
                ? idempotencyKey
                : (parameter.getUserId() + ":" + idempotencyKey);

        boolean isIdempotencyProcessing = false;

        try {
            String currentPayload = parameter.getOrderId() + ":" + parameter.getPgToken();
            var validationResult = kakaoPayIdempotencyService.validate(namespacedKey, currentPayload);

            if (validationResult.isCached()) {
                return (KaKaoPayApproveDTO.Response) validationResult.getCachedResponse();
            }

            isIdempotencyProcessing = true;

            // 락 획득 및 비즈니스 로직 실행
            return kakaoPayLockService.executeWithLock(parameter.getOrderId(), () -> {

                // 중복 처리 방어: 이미 완료된 주문인지 확인
                if (itemPurchaseRepository.existsByOrderId(parameter.getOrderId())) {
                    log.warn("이미 처리된 주문입니다 - orderId: {}", parameter.getOrderId());
                    throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);
                }

                // UNKNOWN 상태 확인 : 사용자 재시도 시 UNKNOWN 상태 확인 후 후처리(보상) 수행
                var existingPaymentOpt = transactionService.findKakaoPayData(parameter.getOrderId());
                if (existingPaymentOpt.isPresent()) {
                    KakaoPayData existingPayment = existingPaymentOpt.get();
                    if (existingPayment.getStatus() == PaymentStatus.UNKNOWN) {
                        log.info("UNKNOWN 상태 결제 재시도 감지 - orderId: {}. 보상 트랜잭션 예약 및 재시도 안내",
                                parameter.getOrderId());

                        // 보상 트랜잭션 예약 (후처리)
                        paymentRecoveryService.scheduleRecovery(
                                parameter.getOrderId(),
                                existingPayment.getTid(),
                                "UNKNOWN 상태 재시도에 의한 후처리");

                        // 사용자에게는 재시도 안내 (202 Accepted)
                        throw new BusinessException(Code.PAYMENT_UNKNOWN_STATUS_RETRY);
                    }
                }

                // 1. 결제 시도 (검증 및 상태 변경)
                KakaoPayData kakaoPayData = transactionService.startPaymentProcessing(parameter);

                // 2. 외부 API 호출(타임아웃 발생 시 API Client에서 자동으로 결제 상태 조회 시도)
                KaKaoPayApproveDTO.KaKaoApiResponse kakaoApiResponse = callKakaoPayApproveApi(parameter, kakaoPayData);

                log.debug("카카오페이 결제 최종 승인 성공 - orderId: {}", parameter.getOrderId());

                // 3. 결제 완료 처리
                KaKaoPayApproveDTO.Response response = transactionService.completePayment(
                        kakaoPayData.getId(), kakaoApiResponse, parameter);

                if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                    kakaoPayIdempotencyService.cacheResponse(namespacedKey, response);
                }

                return response;
            });
        } catch (BusinessException e) {
            // 비즈니스 예외는 그대로 전파 (API 호출 실패 등)
            log.error("결제 승인 처리 실패 (비즈니스 예외) - orderId: {}, error: {}", parameter.getOrderId(), e.getMessage());
            handlePaymentFailure(parameter.getOrderId(), "결제 승인 실패: " + e.getMessage());

            throw e;
        } catch (Exception e) {
            log.error("결제 승인 처리 실패 (예상치 못한 예외) - orderId: {}, error: {}", parameter.getOrderId(), e.getMessage(), e);
            handlePaymentFailure(parameter.getOrderId(), "결제 승인 실패: " + e.getMessage());

            throw e;
        } finally {
            // 멱등성 처리 중 표시 해제 (현재 요청이 처리 중 상태를 점유했던 경우에만)
            if (isIdempotencyProcessing && idempotencyKey != null && !idempotencyKey.isEmpty()) {
                kakaoPayIdempotencyService.unmarkAsProcessing(namespacedKey);
            }
        }
    }

    /**
     * 결제 실패 시 보상 트랜잭션 예약 처리
     */
    private void handlePaymentFailure(String orderId, String errorMessage) {
        try {
            var kakaoPayDataOpt = transactionService.findKakaoPayData(orderId);
            String tid = kakaoPayDataOpt.map(KakaoPayData::getTid).orElse(null);

            paymentRecoveryService.scheduleRecovery(orderId, tid, errorMessage);
        } catch (Exception ex) {
            log.error("보상 트랜잭션 예약 중 오류 - orderId: {}", orderId, ex);
        }
    }

    /**
     * 카카오페이 승인 API 호출
     */
    private KaKaoPayApproveDTO.KaKaoApiResponse callKakaoPayApproveApi(
            KaKaoPayApproveDTO.Parameter parameter, KakaoPayData kakaoPayData) {
        return kaKaoPayApiClient
                .requestPaymentApprove(parameter, kakaoPayData)
                .orElseThrow(() -> new BusinessException(Code.INVALID_KAKAO_API_RESPONSE));
    }
}