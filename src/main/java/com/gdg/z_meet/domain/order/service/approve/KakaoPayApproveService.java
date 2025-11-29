package com.gdg.z_meet.domain.order.service.approve;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
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

            // 보상 처리 예약 (DB 기반 큐)
            // TID는 API 호출 성공 후에만 알 수 있는데, 여기서 알 수 없는 경우가 많음.
            // 하지만 KakaoPayData에 TID가 있다면(API 호출 후 DB 저장 실패 시) 가져올 수 있음.
            // 여기서는 안전하게 null로 넘기고, Consumer가 조회하도록 함.
            // 단, API 호출은 성공했으나 DB 저장이 실패한 경우(망취소)를 대비해 TID를 알 수 있다면 넘겨주는 것이 좋음.
            // 현재 구조상 API 호출 결과(kakaoApiResponse)를 catch 블록에서 알기 어려우므로,
            // Consumer(Scheduler)가 KakaoPayData를 조회해서 TID가 있으면 취소하는 방식으로 처리.
            paymentRecoveryService.scheduleRecovery(parameter.getOrderId(), null, "결제 승인 실패: " + e.getMessage());

            throw e;
        } finally {
            // 멱등성 처리 중 표시 해제 (현재 요청이 처리 중 상태를 점유했던 경우에만)
            if (isIdempotencyProcessing && idempotencyKey != null && !idempotencyKey.isEmpty()) {
                kakaoPayIdempotencyService.unmarkAsProcessing(namespacedKey);
            }
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