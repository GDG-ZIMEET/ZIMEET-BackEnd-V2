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
import org.springframework.transaction.PlatformTransactionManager;
import org.springframework.transaction.TransactionDefinition;
import org.springframework.transaction.support.TransactionTemplate;

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
    private final PlatformTransactionManager transactionManager;

    /**
     * 새로운 트랜잭션에서 락을 획득하기 위한 TransactionTemplate
     * REQUIRES_NEW 전파 전략 사용
     */
    private TransactionTemplate getNewTransactionTemplate() {
        TransactionTemplate template = new TransactionTemplate(transactionManager);
        template.setPropagationBehavior(TransactionDefinition.PROPAGATION_REQUIRES_NEW);
        return template;
    }

    public KaKaoPayApproveDTO.Response approve(KaKaoPayApproveDTO.Parameter parameter, String idempotencyKey) {
        String namespacedKey = null;
        boolean isProcessing = false;

        try {
            // 멱등성 키 네임스페이스: userId:idempotencyKey
            String currentPayload = parameter.getOrderId() + ":" + parameter.getPgToken();
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                namespacedKey = parameter.getUserId() + ":" + idempotencyKey;
                
                // 멱등성 키 검증 (캐시된 응답이 있으면 즉시 반환)
                // validate()는 예외를 던질 수 있음 (payload 불일치, 처리 중인 요청 등)
                var validationResult = kakaoPayIdempotencyService.validate(namespacedKey, currentPayload);
                if (validationResult.isCached()) {
                    return (KaKaoPayApproveDTO.Response) validationResult.getCachedResponse();
                }
                
                // processing 상태로 표시된 경우에만 해제 필요
                if (validationResult.isProcessing()) {
                    isProcessing = true;
                }
            }

            // 락 획득 (별도 트랜잭션에서 수행 - MANDATORY 전파를 위해)
            acquireLockInTransaction(parameter.getOrderId());

            // 중복 처리 방어: 이미 완료된 주문인지 확인
            if (itemPurchaseRepository.existsByOrderId(parameter.getOrderId())) {
                log.warn("이미 처리된 주문입니다 - orderId: {}", parameter.getOrderId());
                throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);
            }

            // 1. 결제 정보 조회 및 검증
            KakaoPayData kakaoPayData = kakaoPayDataRepository.findByOrderId(parameter.getOrderId())
                    .orElseThrow(() -> new BusinessException(Code.PAYMENT_NOT_FOUND));
            
            // 결제 상태 확인
            if (kakaoPayData.getStatus() == PaymentStatus.APPROVED) {
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
            kakaoPayData.setItemPurchase(itemPurchase);

            // 5. 결제 상태 업데이트 (승인 완료)
            kakaoPayData.setStatus(PaymentStatus.APPROVED);
            kakaoPayDataRepository.save(kakaoPayData);

            KaKaoPayApproveDTO.Response response = KaKaoPayApproveConverter.toResponse(kakaoApiResponse, parameter.getOrderId());

            // 응답 캐시 (성공 시에만)
            if (namespacedKey != null) {
                kakaoPayIdempotencyService.cacheResponse(namespacedKey, response);
            }

            return response;
        } finally {
            // 락 해제는 KakaoPayLockService가 트랜잭션 커밋 후 자동으로 처리
            // (KakaoPayLockService.releaseLock은 no-op이지만 명시적으로 주석 추가)
            
            // 멱등성 처리 중 표시 해제 (processing 상태로 표시된 경우에만 해제)
            // validate()에서 예외가 발생하거나 cached 상태인 경우에는 해제하지 않음
            if (namespacedKey != null && isProcessing) {
                kakaoPayIdempotencyService.unmarkAsProcessing(namespacedKey);
            }
        }
    }

    /**
     * 락 획득 (새로운 트랜잭션에서 수행)
     * acquireLock은 MANDATORY 전파를 사용하므로 트랜잭션이 필요함
     * TransactionTemplate을 사용하여 REQUIRES_NEW와 동일한 효과 구현
     */
    private String acquireLockInTransaction(String orderId) {
        return getNewTransactionTemplate().execute(status -> kakaoPayLockService.acquireLock(orderId));
    }
}
