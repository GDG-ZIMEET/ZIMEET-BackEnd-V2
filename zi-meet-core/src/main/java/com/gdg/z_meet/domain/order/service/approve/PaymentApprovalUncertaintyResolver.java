package com.gdg.z_meet.domain.order.service.approve;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.service.recovery.PaymentRecoveryService;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Component;

import java.util.Optional;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentApprovalUncertaintyResolver {

    private final KaKaoPayApiClient kaKaoPayApiClient;
    private final KakaoPayApproveTransactionService transactionService;
    private final PaymentRecoveryService paymentRecoveryService;

    public Optional<KaKaoPayApproveDTO.KaKaoApiResponse> recoverByInquiry(
            KaKaoPayApproveDTO.Parameter parameter,
            KakaoPayData kakaoPayData,
            String cause) {
        log.warn("카카오페이 승인 결과 불확실. 상태 조회 시도 - orderId: {}, cause: {}",
                parameter.getOrderId(), cause);

        Optional<KaKaoPayApproveDTO.KaKaoApiResponse> inquiryResult =
                kaKaoPayApiClient.inquirePaymentStatus(kakaoPayData.getTid())
                        .filter(res -> "SUCCESS_PAYMENT".equals(res.getStatus()));

        if (inquiryResult.isPresent()) {
            log.info("상태 조회로 결제 성공 확인 - orderId: {}", parameter.getOrderId());
            return inquiryResult;
        }

        markUnknownAndScheduleRecovery(parameter.getOrderId(), kakaoPayData.getTid(), cause);
        throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);
    }

    private void markUnknownAndScheduleRecovery(String orderId, String tid, String cause) {
        log.warn("결제 결과를 확정할 수 없어 UNKNOWN 전환 후 보상 예약 - orderId: {}, cause: {}", orderId, cause);

        try {
            transactionService.findKakaoPayData(orderId)
                    .ifPresent(data -> {
                        transactionService.updateStatus(data.getId(), PaymentStatus.UNKNOWN);
                        log.info("결제 상태 UNKNOWN 변경 완료 - orderId: {}", orderId);
                    });
            paymentRecoveryService.scheduleRecovery(orderId, tid, cause);
        } catch (Exception e) {
            log.error("UNKNOWN 처리 중 오류 발생 - orderId: {}", orderId, e);
        }
    }
}
