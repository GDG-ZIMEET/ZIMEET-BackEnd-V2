package com.gdg.z_meet.domain.order.service.ready;

import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.order.dto.KaKaoPayReadyDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.entity.enums.ProductType;
import com.gdg.z_meet.domain.order.ledger.PaymentLedgerActorType;
import com.gdg.z_meet.domain.order.ledger.PaymentLedgerRecorder;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
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
public class KakaoPayReadyTransactionService {

    private final UserRepository userRepository;
    private final UserTeamRepository userTeamRepository;
    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final PaymentLedgerRecorder paymentLedgerRecorder;

    /**
     * 주문자 검증 및 조회
     */
    @Transactional(readOnly = true, propagation = Propagation.REQUIRES_NEW)
    public User validateAndGetBuyer(KaKaoPayReadyDTO.Parameter parameter) {
        // 1. 주문자 조회
        User buyer = userRepository.findById(parameter.getBuyerId())
                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));

        // 2. 상품 타입 검증
        validateProductType(parameter);

        return buyer;
    }

    /** 외부 PG 호출 전에 주문과 멱등 키를 먼저 영속화한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KakaoPayData reserveReadyData(KaKaoPayReadyDTO.Parameter parameter, String orderId,
                                         User buyer, String readyIdempotencyKey,
                                         String requestFingerprint) {
        if (readyIdempotencyKey != null) {
            var existing = kakaoPayDataRepository.findByReadyIdempotencyKey(readyIdempotencyKey);
            if (existing.isPresent()) {
                if (!java.util.Objects.equals(existing.get().getReadyRequestFingerprint(), requestFingerprint)) {
                    throw new BusinessException(Code.IDEMPOTENCY_PAYLOAD_MISMATCH);
                }
                return existing.get();
            }
        }

        return kakaoPayDataRepository.save(KakaoPayData.builder()
                .orderId(orderId)
                .readyIdempotencyKey(readyIdempotencyKey)
                .readyRequestFingerprint(requestFingerprint)
                .status(PaymentStatus.PREPARED)
                .productType(ProductType.valueOf(parameter.getProductType()))
                .totalPrice(parameter.getTotalPrice())
                .buyer(buyer)
                .build());
    }

    /** PG Ready 응답과 PREPARED 원장을 같은 트랜잭션에 확정한다. */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public KakaoPayData completeReadyData(Long paymentId, KaKaoPayReadyDTO.KakaoApiResponse kakaoApiResponse) {
        KakaoPayData kaKaoPayData = kakaoPayDataRepository.findById(paymentId)
                .orElseThrow(() -> new BusinessException(Code.PAYMENT_NOT_FOUND));
        if (kaKaoPayData.getTid() != null && kaKaoPayData.getReadyRedirectUrl() != null) {
            return kaKaoPayData;
        }
        kaKaoPayData.setTid(kakaoApiResponse.getTid());
        kaKaoPayData.setReadyRedirectUrl(kakaoApiResponse.getNext_redirect_pc_url());
        paymentLedgerRecorder.record(
                kaKaoPayData,
                null,
                PaymentStatus.PREPARED,
                PaymentLedgerActorType.USER,
                String.valueOf(kaKaoPayData.getBuyer().getId()),
                "Payment ready data created",
                "ready-" + kaKaoPayData.getOrderId(),
                "source=kakao_pay_ready"
        );
        log.debug("결제 준비 데이터 저장 완료 - orderId: {}", kaKaoPayData.getOrderId());
        return kaKaoPayData;
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
}
