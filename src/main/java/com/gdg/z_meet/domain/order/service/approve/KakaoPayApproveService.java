package com.gdg.z_meet.domain.order.service.approve;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.repository.KakaoItemPurchaseRepository;

import com.gdg.z_meet.domain.order.service.idempotency.KakaoPayIdempotencyService;
import com.gdg.z_meet.domain.order.service.locking.KakaoPayLockService;
import com.gdg.z_meet.domain.order.service.PaymentCompensationProducer;
import com.gdg.z_meet.domain.order.service.cancel.KakaoPayCancelService;
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
    private final PaymentCompensationProducer paymentCompensationProducer;
    private final KakaoPayCancelService kakaoPayCancelService;
    private final KakaoPayApproveTransactionService transactionService;

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

                // 1. 결제 시도 (검증 및 상태 변경)
                KakaoPayData kakaoPayData = transactionService.startPaymentProcessing(parameter);

                // 2. 외부 API 호출
                KaKaoPayApproveDTO.KaKaoApiResponse kakaoApiResponse = callKakaoPayApproveApi(parameter, kakaoPayData);

                log.debug("카카오페이 결제 최종 승인 성공 - orderId: {}", parameter.getOrderId());

                // 3. 결제 완료 처리
                KaKaoPayApproveDTO.Response response = transactionService.completePayment(
                        kakaoPayData.getId(), kakaoApiResponse, parameter);

                // 응답 캐시
                if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                    kakaoPayIdempotencyService.cacheResponse(namespacedKey, response);
                }

                return response;
            });

        } catch (Exception e) {
            log.error("결제 승인 처리 실패 - orderId: {}, error: {}",
                    parameter.getOrderId(), e.getMessage(), e);

            // 보상 처리 시도
            handleCompensation(parameter, e);

            throw e;
        } finally {
            // 멱등성 처리 중 표시 해제 (현재 요청이 처리 중 상태를 점유했던 경우에만)
            if (isIdempotencyProcessing && idempotencyKey != null && !idempotencyKey.isEmpty()) {
                kakaoPayIdempotencyService.unmarkAsProcessing(namespacedKey);
            }
        }
    }

    private void handleCompensation(KaKaoPayApproveDTO.Parameter parameter, Exception e) {
        try {
            // DB에서 결제 데이터 조회 (트랜잭션이 커밋되었을 가능성이 있으므로 조회 시도)
            // 주의: startPaymentProcessing이 실패했다면 데이터가 없을 수도 있음
            // 따라서 예외 처리를 통해 안전하게 접근
            transactionService.findKakaoPayData(parameter.getOrderId()).ifPresent(kakaoPayData -> {
                String failureStep = determineFailureStep(e);

                // API 호출 성공 후 내부 처리 실패인 경우 (TID가 있어야 함)
                // 하지만 여기서 TID를 알기 어려우므로, KakaoPayData에 TID가 저장되어 있는지 확인하거나
                // 단순히 실패 상태로만 변경할지 결정해야 함.
                // 현재 구조에서는 API 호출 후 TID를 DB에 저장하는 단계가 completePayment에 있으므로,
                // API 호출은 성공했으나 DB 저장이 안 된 상태일 수 있음.
                // 이 경우 TID를 모르므로 망취소(전체 취소)가 어려울 수 있음.

                // 만약 API 호출 전 실패라면 단순히 FAILED로 변경
                transactionService.markAsFailed(kakaoPayData.getId(), "결제 승인 실패: " + e.getMessage());

                // 비동기 보상 메시지 발행 (필요한 경우)
                // TID가 없으면 보상 처리가 제한적일 수 있음
                if (kakaoPayData.getTid() != null) {
                    compensatePaymentAsync(kakaoPayData, kakaoPayData.getTid(),
                            parameter.getUserId(), "내부 처리 실패: " + e.getMessage(), failureStep);
                }
            });
        } catch (Exception ex) {
            log.error("보상 처리 중 오류 발생 - orderId: {}", parameter.getOrderId(), ex);
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

    /**
     * 비동기 보상 트랜잭션 실행
     */
    private void compensatePaymentAsync(KakaoPayData kakaoPayData, String tid, Long userId,
            String cancelReason, String failureStep) {
        try {
            paymentCompensationProducer.sendCompensationMessage(
                    kakaoPayData.getOrderId(),
                    tid,
                    userId,
                    cancelReason,
                    failureStep);
            log.info("보상 트랜잭션 메시지 발행 완료 - orderId: {}", kakaoPayData.getOrderId());
        } catch (Exception e) {
            log.error("보상 트랜잭션 메시지 발행 실패 - orderId: {}, error: {}",
                    kakaoPayData.getOrderId(), e.getMessage(), e);
            // 동기적으로 보상 처리 시도
            try {
                kakaoPayCancelService.compensatePayment(kakaoPayData, cancelReason);
            } catch (Exception ex) {
                log.error("동기 보상 처리도 실패 - orderId: {}", kakaoPayData.getOrderId(), ex);
            }
        }
    }

    /**
     * 실패 단계 판단
     */
    private String determineFailureStep(Exception e) {
        String message = e.getMessage();
        if (message != null) {
            if (message.contains("PRODUCT_PROCESSING")) {
                return "PRODUCT_PROCESSING";
            } else if (message.contains("ITEM_PURCHASE_CREATION")) {
                return "ITEM_PURCHASE_CREATION";
            } else if (message.contains("STATUS_UPDATE")) {
                return "STATUS_UPDATE";
            }
        }
        return "UNKNOWN";
    }
}