package com.gdg.z_meet.domain.order.service.sync;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.domain.order.service.approve.KakaoPayApproveTransactionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentSyncService {

    private final KaKaoPayApiClient kaKaoPayApiClient;
    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final KakaoPayApproveTransactionService approveTransactionService;

    /**
     * 외부 PG사와 상태를 동기화하여 결제 상태를 확정함
     * 
     * @param orderId 주문 ID
     * @return 동기화 결과 (성공 시 true)
     */
    @Transactional
    public boolean syncPaymentStatus(String orderId) {
        log.info("결제 상태 동기화 시작 - orderId: {}", orderId);

        KakaoPayData kakaoPayData = kakaoPayDataRepository.findByOrderId(orderId)
                .orElse(null);

        if (kakaoPayData == null) {
            log.warn("동기화 대상을 찾을 수 없음 - orderId: {}", orderId);
            return false;
        }

        // 이미 완료된 건은 스킵
        if (kakaoPayData.getStatus() == PaymentStatus.APPROVED ||
                kakaoPayData.getStatus() == PaymentStatus.CANCELLED) {
            log.info("이미 확정된 결제입니다 - orderId: {}, status: {}", orderId, kakaoPayData.getStatus());
            return true;
        }

        if (kakaoPayData.getTid() == null || kakaoPayData.getTid().isEmpty()) {
            log.warn("TID가 없어 동기화 불가 - orderId: {}", orderId);
            return false;
        }

        // 1. 카카오페이 상태 조회
        Optional<KaKaoPayApproveDTO.KaKaoApiResponse> inquiryResult = kaKaoPayApiClient
                .inquirePaymentStatus(kakaoPayData.getTid());

        if (inquiryResult.isPresent()) {
            KaKaoPayApproveDTO.KaKaoApiResponse response = inquiryResult.get();
            String pgStatus = response.getStatus();
            log.info("카카오페이 조회 결과 - orderId: {}, pgStatus: {}", orderId, pgStatus);

            switch (pgStatus) {
                case "SUCCESS_PAYMENT":
                    // 실제로는 결제 성공인데 우리 DB만 미완료인 경우 -> 승인 처리
                    log.info("결제 성공 확인됨. 승인 처리 진행 - orderId: {}", orderId);
                    approveTransactionService.completePayment(kakaoPayData.getId(), response, null);
                    return true;

                case "CANCEL_PAYMENT":
                case "FAIL_PAYMENT":
                    log.info("결제 취소/실패 확인됨. 상태 업데이트 - orderId: {}", orderId);
                    kakaoPayData.setStatus(
                            pgStatus.equals("CANCEL_PAYMENT") ? PaymentStatus.CANCELLED : PaymentStatus.FAILED);
                    kakaoPayDataRepository.save(kakaoPayData);
                    return true;

                default:
                    log.warn("알 수 없는 PG 상태 - orderId: {}, pgStatus: {}", orderId, pgStatus);
                    return false;
            }
        }

        log.warn("PG사 상태 조회 실패 - orderId: {}", orderId);
        return false;
    }
}