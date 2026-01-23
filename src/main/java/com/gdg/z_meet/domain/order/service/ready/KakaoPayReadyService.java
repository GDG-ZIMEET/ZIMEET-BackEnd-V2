package com.gdg.z_meet.domain.order.service.ready;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.converter.KaKaoPayReadyConverter;
import com.gdg.z_meet.domain.order.dto.KaKaoPayReadyDTO;
import com.gdg.z_meet.domain.order.service.idempotency.KakaoPayIdempotencyService;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import io.github.resilience4j.bulkhead.annotation.Bulkhead;
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

    @Bulkhead(name = "paymentBulkhead")
    public KaKaoPayReadyDTO.Response ready(KaKaoPayReadyDTO.Parameter parameter, String idempotencyKey) {
        String namespacedKey = null;
        boolean isIdempotencyProcessing = false;
        try {
            // 멱등성 키 네임스페이스: userId:idempotencyKey
            namespacedKey = (idempotencyKey == null || idempotencyKey.isEmpty())
                    ? idempotencyKey
                    : (parameter.getBuyerId() + ":" + idempotencyKey);

            // 멱등성 키 검증
            String currentPayload = parameter.getTeamId() + ":" + parameter.getProductType() + ":"
                    + parameter.getTotalPrice();
            var validationResult = kakaoPayIdempotencyService.validate(namespacedKey, currentPayload);

            if (validationResult.isCached()) {
                return (KaKaoPayReadyDTO.Response) validationResult.getCachedResponse();
            }

            isIdempotencyProcessing = true;

            // 1. 주문자 정보 및 결제할 상품 검증
            User buyer = transactionService.validateAndGetBuyer(parameter);

            // 2. 결제 준비 API 호출 (주문 ID 할당)
            String orderId = createOrderId();

            KaKaoPayReadyDTO.KakaoApiResponse kakaoApiResponse = kaKaoPayApiClient
                    .requestPaymentReady(parameter, orderId, buyer)
                    .orElseThrow(() -> new BusinessException(Code.KAKAO_API_RESPONSE_ERROR));

            log.debug("KaKaoPay 결제 준비 성공. 주문 ID : {}", orderId);

            if (kakaoApiResponse.getTid() == null || kakaoApiResponse.getNext_redirect_pc_url() == null) {
                throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);
            }

            // 3. 결제 정보 DB 저장
            transactionService.saveReadyData(kakaoApiResponse, parameter, orderId, buyer);

            KaKaoPayReadyDTO.Response response = KaKaoPayReadyConverter.toResponse(kakaoApiResponse, orderId);

            // 응답 캐시
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                kakaoPayIdempotencyService.cacheResponse(namespacedKey, response);
            }

            return response;
        } finally {
            // 멱등성 처리 중 표시 해제 (현재 요청이 처리 중 상태를 점유했던 경우에만)
            if (isIdempotencyProcessing && idempotencyKey != null && !idempotencyKey.isEmpty()) {
                kakaoPayIdempotencyService.unmarkAsProcessing(namespacedKey);
            }
        }
    }

    private String createOrderId() {
        return UUID.randomUUID().toString();
    }
}
