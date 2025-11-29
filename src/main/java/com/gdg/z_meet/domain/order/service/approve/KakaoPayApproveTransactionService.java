package com.gdg.z_meet.domain.order.service.approve;

import com.gdg.z_meet.domain.order.converter.KaKaoPayApproveConverter;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.ItemPurchase;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.entity.enums.ProductType;
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
public class KakaoPayApproveTransactionService {

    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final KakaoItemPurchaseRepository itemPurchaseRepository;
    private final KakaoItemProcessor kakaoItemProcessor;

    /**
     * 결제 시도 (검증 및 상태 변경)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KakaoPayData startPaymentProcessing(KaKaoPayApproveDTO.Parameter parameter) {
        KakaoPayData kakaoPayData = kakaoPayDataRepository.findByOrderId(parameter.getOrderId())
                .orElseThrow(() -> new BusinessException(Code.PAYMENT_NOT_FOUND));

        // 결제 상태 검증
        if (kakaoPayData.getStatus() == PaymentStatus.APPROVED) {
            log.warn("이미 승인된 결제입니다 - orderId: {}", parameter.getOrderId());
            throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);
        }

        // 주문자 검증
        if (!kakaoPayData.getBuyer().getId().equals(parameter.getUserId())) {
            throw new BusinessException(Code.KAKAO_API_INVALID_BUYER);
        }

        // 상태 변경 (PREPARED -> PROCESSING)
        kakaoPayData.setStatus(PaymentStatus.PROCESSING);

        // 변경 감지로 저장되지만 명시적으로 호출
        return kakaoPayDataRepository.save(kakaoPayData);
    }

    /**
     * 결제 완료 처리 (상품 지급 및 최종 승인)
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KaKaoPayApproveDTO.Response completePayment(
            Long kakaoPayDataId,
            KaKaoPayApproveDTO.KaKaoApiResponse kakaoApiResponse,
            KaKaoPayApproveDTO.Parameter parameter) {

        KakaoPayData kakaoPayData = kakaoPayDataRepository.findById(kakaoPayDataId)
                .orElseThrow(() -> new BusinessException(Code.PAYMENT_NOT_FOUND));

        ProductType productType = kakaoPayData.getProductType();
        Long totalPrice = kakaoPayData.getTotalPrice();
        User buyer = kakaoPayData.getBuyer();

        // 1. 상품 지급 처리
        KakaoItemProcessor.ProcessResult processResult;
        try {
            processResult = kakaoItemProcessor.processProduct(productType, totalPrice, buyer);
        } catch (Exception e) {
            log.error("상품 지급 실패 - orderId: {}", kakaoPayData.getOrderId(), e);
            throw new RuntimeException("PRODUCT_PROCESSING", e);
        }

        // 2. 구매 내역 생성
        try {
            ItemPurchase itemPurchase = KaKaoPayApproveConverter.toItemPurchase(
                    kakaoApiResponse, kakaoPayData, buyer,
                    processResult.team(), processResult.userProfile());
            itemPurchaseRepository.save(itemPurchase);
            kakaoPayData.setItemPurchase(itemPurchase);
        } catch (Exception e) {
            log.error("구매 내역 생성 실패 - orderId: {}", kakaoPayData.getOrderId(), e);
            throw new RuntimeException("ITEM_PURCHASE_CREATION", e);
        }

        // 3. 최종 상태 업데이트
        try {
            kakaoPayData.setStatus(PaymentStatus.APPROVED);
            kakaoPayDataRepository.save(kakaoPayData);
        } catch (Exception e) {
            log.error("상태 업데이트 실패 - orderId: {}", kakaoPayData.getOrderId(), e);
            throw new RuntimeException("STATUS_UPDATE", e);
        }

        return KaKaoPayApproveConverter.toResponse(kakaoApiResponse, parameter.getOrderId());
    }

    /**
     * 결제 실패 처리
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long kakaoPayDataId, String reason) {
        try {
            KakaoPayData kakaoPayData = kakaoPayDataRepository.findById(kakaoPayDataId)
                    .orElseThrow(() -> new BusinessException(Code.PAYMENT_NOT_FOUND));

            kakaoPayData.setStatus(PaymentStatus.FAILED);
            kakaoPayDataRepository.save(kakaoPayData);
            log.warn("결제 실패 처리 완료 - orderId: {}, reason: {}", kakaoPayData.getOrderId(), reason);
        } catch (Exception e) {
            log.error("결제 실패 상태 변경 중 오류 발생 - kakaoPayDataId: {}", kakaoPayDataId, e);
        }
    }

    /**
     * 주문 ID로 결제 데이터 조회 (보상 트랜잭션용)
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public java.util.Optional<KakaoPayData> findKakaoPayData(String orderId) {
        return kakaoPayDataRepository.findByOrderId(orderId);
    }
}