package com.gdg.z_meet.domain.order.service.approve;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.service.locking.KakaoPayLockService;
import com.gdg.z_meet.domain.order.service.recovery.PaymentRecoveryService;
import com.gdg.z_meet.domain.order.service.idempotency.KakaoPayIdempotencyService;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPayApproveService {

    private final KaKaoPayApiClient kaKaoPayApiClient;
    private final KakaoPayLockService kakaoPayLockService;
    private final KakaoPayApproveTransactionService transactionService;
    private final PaymentRecoveryService paymentRecoveryService;
    private final KakaoPayIdempotencyService idempotencyService;
    private final PaymentApprovalUncertaintyResolver uncertaintyResolver;

    /**
     * MQ를 이용한 비동기 결제 승인 요청
     */
    public KaKaoPayApproveDTO.Response approve(KaKaoPayApproveDTO.Parameter parameter, String idempotencyKey) {
        String key = (idempotencyKey == null || idempotencyKey.isBlank())
                ? null
                : parameter.getUserId() + ":APPROVE:" + idempotencyKey;
        String payload = parameter.getOrderId() + ":" + parameter.getPgToken();
        var validation = idempotencyService.validate(key, payload);
        if (validation.isCached()) {
            return (KaKaoPayApproveDTO.Response) validation.getCachedResponse();
        }

        try {
            log.info("결제 승인 요청 MQ 적재 시작 - orderId: {}", parameter.getOrderId());
            KakaoPayData payment = transactionService.startPaymentProcessing(parameter);
            validateApprovalState(payment);

            log.info("결제 승인 요청 아웃박스 적재 완료 - orderId: {}", parameter.getOrderId());

            KaKaoPayApproveDTO.Response response = KaKaoPayApproveDTO.Response.builder()
                    .orderId(parameter.getOrderId())
                    .approvedAt(java.time.LocalDateTime.now().toString())
                    .build();
            idempotencyService.cacheResponse(key, response);
            return response;
        } finally {
            idempotencyService.unmarkAsProcessing(key, validation.getProcessingToken());
        }
    }

    /**
     * 실제 결제 승인 로직 (워커에서 호출)
     */
    public KaKaoPayApproveDTO.Response processApproval(KaKaoPayApproveDTO.Parameter parameter) {
        try {
            // 락 획득 및 비즈니스 로직 실행 (워커에서 네임드락 수행)
            return kakaoPayLockService.executeWithLock(parameter.getOrderId(), () -> {

                // 1. 결제 데이터 조회 및 최종 상태 확인 (이미 처리 중이거나 완료되었는지)
                KakaoPayData kakaoPayData = transactionService.startPaymentProcessing(parameter);

                if (kakaoPayData.getStatus() == PaymentStatus.APPROVED) {
                    return KaKaoPayApproveDTO.Response.builder()
                            .orderId(kakaoPayData.getOrderId())
                            .approvedAt(kakaoPayData.getUpdatedAt().toString())
                            .build();
                }
                validateApprovalState(kakaoPayData);

                // 2. 외부 API 호출 (카카오페이 승인)
                Optional<KaKaoPayApproveDTO.KaKaoApiResponse> kakaoApiResponseOpt;
                try {
                    kakaoApiResponseOpt = kaKaoPayApiClient.requestPaymentApprove(parameter, kakaoPayData);
                } catch (Exception e) {
                    kakaoApiResponseOpt = uncertaintyResolver.recoverByInquiry(
                            parameter, kakaoPayData, "APPROVE_EXCEPTION:" + e.getClass().getSimpleName());
                }

                if (kakaoApiResponseOpt.isEmpty()) {
                    // 회로차단(CircuitBreaker) fallback 등으로 빈 응답이 반환된 경우에 대한 방어 로직
                    kakaoApiResponseOpt = uncertaintyResolver.recoverByInquiry(
                            parameter, kakaoPayData, "APPROVE_EMPTY_RESPONSE");
                }

                KaKaoPayApproveDTO.KaKaoApiResponse kakaoApiResponse = kakaoApiResponseOpt.get();
                log.info("카카오페이 결제 최종 승인 성공 (워커) - orderId: {}", parameter.getOrderId());

                // 3. 결제 완료 처리 (상품 지급 및 구매 내역 생성)
                return transactionService.completePayment(
                        kakaoPayData.getId(), kakaoApiResponse, parameter);
            });
        } catch (BusinessException e) {
            log.error("워커 결제 승인 처리 실패 (비즈니스 예외) - orderId: {}, error: {}", parameter.getOrderId(), e.getMessage());
            if (e.getCode() == Code.IDEMPOTENCY_CONFLICT) {
                throw e;
            }
            // 이미 UNKNOWN 처리가 된 경우는 중복 호출 방지
            handlePaymentFailure(parameter.getOrderId(), "워커 승인 실패: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("워커 결제 승인 처리 실패 (예상치 못한 예외) - orderId: {}, error: {}", parameter.getOrderId(), e.getMessage(), e);
            handlePaymentFailure(parameter.getOrderId(), "워커 승인 실패: " + e.getMessage());
            throw e;
        }
    }

    private void validateApprovalState(KakaoPayData payment) {
        if (payment.getStatus() == PaymentStatus.PROCESSING ||
                payment.getStatus() == PaymentStatus.APPROVED) {
            return;
        }
        if (payment.getStatus() == PaymentStatus.UNKNOWN) {
            throw new BusinessException(Code.PAYMENT_UNKNOWN_STATUS_RETRY);
        }
        throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);
    }

    /**
     * 결제 실패 시 보상 트랜잭션 예약 처리
     */
    private void handlePaymentFailure(String orderId, String errorMessage) {
        try {
            var kakaoPayDataOpt = transactionService.findKakaoPayData(orderId);
            if (kakaoPayDataOpt.isPresent()) {
                KakaoPayData data = kakaoPayDataOpt.get();
                // 이미 APPROVED나 UNKNOWN인 경우 보상 트랜잭션 예약을 신중히 결정
                if (data.getStatus() != PaymentStatus.APPROVED && data.getStatus() != PaymentStatus.UNKNOWN) {
                    paymentRecoveryService.scheduleRecovery(orderId, data.getTid(), errorMessage);
                }
            }
        } catch (Exception ex) {
            log.error("보상 트랜잭션 예약 중 오류 - orderId: {}", orderId, ex);
        }
    }
}
