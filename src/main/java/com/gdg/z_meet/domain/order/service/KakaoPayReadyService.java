package com.gdg.z_meet.domain.order.service;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.converter.KaKaoPayReadyConverter;
import com.gdg.z_meet.domain.order.dto.KaKaoPayReadyDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.ProductType;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPayReadyService {

    private final UserRepository userRepository;
    private final KaKaoPayApiClient kaKaoPayApiClient;
    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final UserTeamRepository userTeamRepository;
    private final KakaoPayIdempotencyService kakaoPayIdempotencyService;

    @Transactional
    public KaKaoPayReadyDTO.Response ready(KaKaoPayReadyDTO.Parameter parameter, String idempotencyKey) {
        try {
            // 멱등성 키 네임스페이스: userId:idempotencyKey
            String namespacedKey = (idempotencyKey == null || idempotencyKey.isEmpty())
                    ? idempotencyKey
                    : (parameter.getBuyerId() + ":" + idempotencyKey);
            
            // 멱등성 키 검증
            String currentPayload = parameter.getTeamId() + ":" + parameter.getProductType() + ":" + parameter.getTotalPrice();
            var validationResult = kakaoPayIdempotencyService.validate(namespacedKey, currentPayload);
            
            if (validationResult.isCached()) {
                return (KaKaoPayReadyDTO.Response) validationResult.getCachedResponse();
            }

            // 1. 주문자 정보 및 결제할 상품 검증
            User buyer = userRepository.findById(parameter.getBuyerId())
                    .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));

            validateProductType(parameter);

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
            KakaoPayData kaKaoPayData = KaKaoPayReadyConverter.toKakaoPayData(
                    kakaoApiResponse, parameter, orderId, buyer);
            kakaoPayDataRepository.save(kaKaoPayData);

            KaKaoPayReadyDTO.Response response = KaKaoPayReadyConverter.toResponse(kakaoApiResponse, orderId);

            // 응답 캐시
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                kakaoPayIdempotencyService.cacheResponse(namespacedKey, response);
            }

            return response;
        } finally {
            // 멱등성 처리 중 표시 해제
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                String namespacedKey = parameter.getBuyerId() + ":" + idempotencyKey;
                kakaoPayIdempotencyService.unmarkAsProcessing(namespacedKey);
            }
        }
    }

    private void validateProductType(KaKaoPayReadyDTO.Parameter parameter) {
        if (!ProductType.isValid(parameter.getProductType())) {
            throw new BusinessException(Code.INVALID_PRODUCT_TYPE);
        }

        ProductType productType = ProductType.valueOf(parameter.getProductType());
        
        // TICKET, SEASON이 아닌 경우 (즉, TWO_TO_TWO, THREE_TO_THREE인 경우) 팀 멤버 검증
        if (productType != ProductType.TICKET && productType != ProductType.SEASON) {
            boolean isMember = userTeamRepository.existsByUserIdAndTeamIdAndActiveStatus(
                    parameter.getBuyerId(), parameter.getTeamId());
            if (!isMember) {
                throw new BusinessException(Code.TEAM_USER_NOT_FOUND);
            }
        }
    }

    private String createOrderId() {
        return UUID.randomUUID().toString();
    }
}
