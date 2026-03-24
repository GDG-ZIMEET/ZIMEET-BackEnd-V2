package com.gdg.z_meet.domain.order.service.cancel;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
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
public class KakaoPayCancelTransactionService {

    private final KakaoPayDataRepository kakaoPayDataRepository;

    /**
     * 결제 취소 전 데이터 조회 및 검증
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public KakaoPayData validateAndGetPaymentData(String orderId, Long userId) {
        KakaoPayData kakaoPayData = kakaoPayDataRepository.findByOrderId(orderId)
                .orElseThrow(() -> new BusinessException(Code.PAYMENT_NOT_FOUND));

        // 권한 확인
        if (!kakaoPayData.getBuyer().getId().equals(userId)) {
            throw new BusinessException(Code.KAKAO_API_INVALID_BUYER);
        }

        // 이미 취소된 결제인지 확인
        if (kakaoPayData.getStatus() == PaymentStatus.CANCELLED) {
            log.warn("이미 취소된 결제입니다 - orderId: {}", orderId);
            throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);
        }

        // 취소 불가능한 상태 확인
        if (kakaoPayData.getStatus() != PaymentStatus.PREPARED &&
                kakaoPayData.getStatus() != PaymentStatus.PROCESSING &&
                kakaoPayData.getStatus() != PaymentStatus.APPROVED) {
            log.warn("취소할 수 없는 결제 상태입니다 - orderId: {}, status: {}",
                    orderId, kakaoPayData.getStatus());
            throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);
        }

        // TID가 없으면 취소 불가
        if (kakaoPayData.getTid() == null || kakaoPayData.getTid().isEmpty()) {
            log.warn("TID가 없어 취소할 수 없습니다 - orderId: {}", orderId);
            throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);
        }

        return kakaoPayData;
    }

    /**
     * 취소 완료 상태 업데이트
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void completeCancel(Long kakaoPayDataId) {
        KakaoPayData kakaoPayData = kakaoPayDataRepository.findById(kakaoPayDataId)
                .orElseThrow(() -> new BusinessException(Code.PAYMENT_NOT_FOUND));

        kakaoPayData.setStatus(PaymentStatus.CANCELLED);
        kakaoPayDataRepository.save(kakaoPayData);
    }

    /**
     * 실패 상태 업데이트
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void markAsFailed(Long kakaoPayDataId) {
        try {
            KakaoPayData kakaoPayData = kakaoPayDataRepository.findById(kakaoPayDataId)
                    .orElseThrow(() -> new BusinessException(Code.PAYMENT_NOT_FOUND));

            kakaoPayData.setStatus(PaymentStatus.FAILED);
            kakaoPayDataRepository.save(kakaoPayData);
        } catch (Exception e) {
            log.error("상태 업데이트 실패 - kakaoPayDataId: {}, error: {}", kakaoPayDataId, e.getMessage(), e);
        }
    }

    /**
     * 상태 업데이트
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void updateStatus(Long kakaoPayDataId, PaymentStatus status) {
        KakaoPayData kakaoPayData = kakaoPayDataRepository.findById(kakaoPayDataId)
                .orElseThrow(() -> new BusinessException(Code.PAYMENT_NOT_FOUND));
        kakaoPayData.setStatus(status);
        kakaoPayDataRepository.save(kakaoPayData);
    }
}