package com.gdg.z_meet.domain.order.service;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.converter.KaKaoPayApproveConverter;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.ItemPurchase;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.PaymentStatus;
import com.gdg.z_meet.domain.order.entity.ProductType;
import com.gdg.z_meet.domain.order.repository.KakaoItemPurchaseRepository;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.domain.user.entity.User;
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
public class KakaoPayApproveService {

    private final KaKaoPayApiClient kaKaoPayApiClient;
    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final KakaoItemPurchaseRepository itemPurchaseRepository;
    private final KakaoPayIdempotencyService kakaoPayIdempotencyService;
    private final KakaoItemProcessor kakaoItemProcessor;
    private final KakaoPayLockService kakaoPayLockService;

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KaKaoPayApproveDTO.Response approve(KaKaoPayApproveDTO.Parameter parameter, String idempotencyKey) {

        String lockName = null;

        try {
            // 멱등성 키 검증 (공통 로직)
            String currentPayload = parameter.getOrderId() + ":" + parameter.getPgToken();
            var validationResult = kakaoPayIdempotencyService.validate(idempotencyKey, currentPayload);
            
            if (validationResult.isCached()) {
                return (KaKaoPayApproveDTO.Response) validationResult.getCachedResponse();
            }

            // 락 획득
            lockName = kakaoPayLockService.acquireLock(parameter.getOrderId());

            // 중복 처리 방어: 이미 완료된 주문인지 확인
            if (itemPurchaseRepository.existsByOrderId(parameter.getOrderId())) {
                log.warn("이미 처리된 주문입니다 - orderId: {}", parameter.getOrderId());
                throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);
            }

            // 1. 결제 정보 조회 및 검증
            KakaoPayData kakaoPayData = kakaoPayDataRepository.findByOrderId(parameter.getOrderId())
                    .orElseThrow(() -> new BusinessException(Code.PAYMENT_NOT_FOUND));
            
            // 결제 상태 확인
            if (kakaoPayData.getStatus() == com.gdg.z_meet.domain.order.entity.PaymentStatus.APPROVED) {
                log.warn("이미 승인된 결제입니다 - orderId: {}", parameter.getOrderId());
                throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);
            }
            
            if (!kakaoPayData.getBuyer().getId().equals(parameter.getUserId())) {
                throw new BusinessException(Code.KAKAO_API_INVALID_BUYER);
            }

            // 2. 최종 승인 API 호출
            KaKaoPayApproveDTO.KaKaoApiResponse kakaoApiResponse = kaKaoPayApiClient
                    .requestPaymentApprove(parameter, kakaoPayData)
                    .orElseThrow(() -> new BusinessException(Code.INVALID_KAKAO_API_RESPONSE));
            
            log.debug("카카오페이 결제 최종 승인 성공");

            ProductType productType = kakaoPayData.getProductType();
            Long totalPrice = kakaoPayData.getTotalPrice();
            User buyer = kakaoPayData.getBuyer();

            // 3. 상품 유형에 따른 처리 (분리된 서비스 사용)
            var processResult = kakaoItemProcessor.processProduct(productType, totalPrice, buyer);

            // 4. 결제 내역 생성
            ItemPurchase itemPurchase = KaKaoPayApproveConverter.toItemPurchase(
                    kakaoApiResponse, kakaoPayData, buyer, processResult.team(), processResult.userProfile());
            itemPurchaseRepository.save(itemPurchase);

            // 5. 결제 상태 업데이트 (승인 완료)
            kakaoPayData.setStatus(PaymentStatus.APPROVED);
            kakaoPayDataRepository.save(kakaoPayData);

            KaKaoPayApproveDTO.Response response = KaKaoPayApproveConverter.toResponse(kakaoApiResponse, parameter.getOrderId());

            // 응답 캐시
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                kakaoPayIdempotencyService.cacheResponse(idempotencyKey, response);
            }

            return response;
        } finally {
            // 락 해제 (분리된 서비스 사용)
            if (lockName != null) {
                kakaoPayLockService.releaseLock(lockName);
            }
            
            // 멱등성 처리 중 표시 해제
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                kakaoPayIdempotencyService.unmarkAsProcessing(idempotencyKey);
            }
        }
    }
}
