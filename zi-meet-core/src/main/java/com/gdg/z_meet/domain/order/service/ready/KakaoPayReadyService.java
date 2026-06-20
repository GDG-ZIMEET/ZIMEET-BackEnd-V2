package com.gdg.z_meet.domain.order.service.ready;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.converter.KaKaoPayReadyConverter;
import com.gdg.z_meet.domain.order.dto.KaKaoPayReadyDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.service.idempotency.KakaoPayIdempotencyService;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPayReadyService {

    private final KaKaoPayApiClient kaKaoPayApiClient;
    private final KakaoPayIdempotencyService kakaoPayIdempotencyService;
    private final KakaoPayReadyTransactionService transactionService;

    public KaKaoPayReadyDTO.Response ready(KaKaoPayReadyDTO.Parameter parameter, String idempotencyKey) {
        String namespacedKey = null;
        String processingToken = null;
        try {
            // 멱등성 키 네임스페이스: userId:idempotencyKey
            namespacedKey = (idempotencyKey == null || idempotencyKey.isEmpty())
                    ? idempotencyKey
                    : (parameter.getBuyerId() + ":READY:" + idempotencyKey);

            // 멱등성 키 검증
            String currentPayload = parameter.getTeamId() + ":" + parameter.getProductType() + ":"
                    + parameter.getTotalPrice();
            var validationResult = kakaoPayIdempotencyService.validate(namespacedKey, currentPayload);

            if (validationResult.isCached()) {
                return (KaKaoPayReadyDTO.Response) validationResult.getCachedResponse();
            }

            processingToken = validationResult.getProcessingToken();

            // 1. 주문자 정보 및 결제할 상품 검증
            User buyer = transactionService.validateAndGetBuyer(parameter);

            // 2. 외부 호출 전에 주문과 멱등 키를 영속화한다.
            KakaoPayData reservation = transactionService.reserveReadyData(
                    parameter, createOrderId(), buyer, namespacedKey, currentPayload);
            String orderId = reservation.getOrderId();

            if (reservation.getTid() != null && reservation.getReadyRedirectUrl() != null) {
                KaKaoPayReadyDTO.Response existingResponse = KaKaoPayReadyDTO.Response.builder()
                        .orderId(orderId)
                        .nextRedirectPcUrl(reservation.getReadyRedirectUrl())
                        .build();
                kakaoPayIdempotencyService.cacheResponse(namespacedKey, existingResponse);
                return existingResponse;
            }

            KaKaoPayReadyDTO.KakaoApiResponse kakaoApiResponse = kaKaoPayApiClient
                    .requestPaymentReady(parameter, orderId, buyer)
                    .orElseThrow(() -> new BusinessException(Code.KAKAO_API_RESPONSE_ERROR));

            log.debug("KaKaoPay 결제 준비 성공. 주문 ID : {}", orderId);

            if (kakaoApiResponse.getTid() == null || kakaoApiResponse.getNext_redirect_pc_url() == null) {
                throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);
            }

            // 3. PG 응답을 예약 주문에 확정
            transactionService.completeReadyData(reservation.getId(), kakaoApiResponse);

            KaKaoPayReadyDTO.Response response = KaKaoPayReadyConverter.toResponse(kakaoApiResponse, orderId);

            // 응답 캐시
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                kakaoPayIdempotencyService.cacheResponse(namespacedKey, response);
            }

            return response;
        } finally {
            // 멱등성 처리 중 표시 해제 (현재 요청이 처리 중 상태를 점유했던 경우에만)
            if (processingToken != null) {
                kakaoPayIdempotencyService.unmarkAsProcessing(namespacedKey, processingToken);
            }
        }
    }

    private String createOrderId() {
        return UUID.randomUUID().toString();
    }
}
