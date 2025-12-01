package com.gdg.z_meet.domain.order.service.cancel;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.converter.KaKaoPayCancelConverter;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.dto.KaKaoPayCancelDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPayCancelService {

    private final KaKaoPayApiClient kaKaoPayApiClient;
    private final KakaoPayCancelTransactionService transactionService;

    /**
     * 결제 취소 처리
     * 
     * @param parameter 취소 파라미터
     * @param userId    사용자 ID
     * @return 취소 응답
     */
    public KaKaoPayCancelDTO.Response cancel(KaKaoPayCancelDTO.Parameter parameter, Long userId) {
        // 1. 결제 정보 조회 및 검증 (Tx 1)
        KakaoPayData kakaoPayData = transactionService.validateAndGetPaymentData(parameter.getOrderId(), userId);

        // 취소 파라미터에 TID와 금액 정보 추가
        parameter = KaKaoPayCancelConverter.toParameter(kakaoPayData, parameter.getCancelReason());

        // 2. 카카오페이 취소 API 호출 (No Tx)
        KaKaoPayCancelDTO.KakaoApiResponse kakaoApiResponse = kaKaoPayApiClient
                .requestPaymentCancel(parameter)
                .orElseThrow(() -> new BusinessException(Code.INVALID_KAKAO_API_RESPONSE));

        log.debug("카카오페이 결제 취소 성공 - orderId: {}", parameter.getOrderId());

        // 3. 결제 상태 업데이트 (Tx 2)
        transactionService.completeCancel(kakaoPayData.getId());

        return KaKaoPayCancelConverter.toResponse(kakaoApiResponse, parameter.getOrderId());
    }

    /**
     * 내부 처리 실패로 인한 자동 취소 (보상 트랜잭션)
     * 
     * 무한 루프 방지:
     * 1. 타임아웃 발생 시 취소 상태 조회 시도
     * 2. 조회 실패 시 UNKNOWN 상태로 저장 (재시도 안내 필요)
     * 3. 이미 UNKNOWN 상태인 경우 재시도 처리
     * 
     * @param kakaoPayData 결제 데이터
     * @param cancelReason 취소 사유
     */
    public void compensatePayment(KakaoPayData kakaoPayData, String cancelReason) {
        try {
            log.warn("보상 트랜잭션 시작 - orderId: {}, reason: {}", kakaoPayData.getOrderId(), cancelReason);

            // 이미 취소되었거나 실패한 경우 스킵 (멱등성 보장)
            if (kakaoPayData.getStatus() == PaymentStatus.CANCELLED ||
                    kakaoPayData.getStatus() == PaymentStatus.FAILED) {
                log.info("이미 취소/실패 처리된 결제입니다 - orderId: {}, status: {}",
                        kakaoPayData.getOrderId(), kakaoPayData.getStatus());
                return;
            }

            // UNKNOWN 상태인 경우 재시도 처리
            if (kakaoPayData.getStatus() == PaymentStatus.UNKNOWN) {
                log.info("UNKNOWN 상태 결제 재처리 시도 - orderId: {}", kakaoPayData.getOrderId());
                // 상태를 PROCESSING으로 변경하여 재시도 (Tx)
                transactionService.updateStatus(kakaoPayData.getId(), PaymentStatus.PROCESSING);
            }

            // TID가 없으면 취소 불가 (PREPARED 상태일 수 있음)
            if (kakaoPayData.getTid() == null || kakaoPayData.getTid().isEmpty()) {
                log.warn("TID가 없어 취소할 수 없습니다. 상태만 FAILED로 변경 - orderId: {}",
                        kakaoPayData.getOrderId());
                transactionService.markAsFailed(kakaoPayData.getId());
                return;
            }

            // 취소 파라미터 생성
            KaKaoPayCancelDTO.Parameter cancelParameter = KaKaoPayCancelConverter.toParameter(
                    kakaoPayData, cancelReason);

            // 카카오페이 취소 API 호출 (No Tx)
            kaKaoPayApiClient.requestPaymentCancel(cancelParameter)
                    .ifPresentOrElse(
                            response -> {
                                log.info("카카오페이 취소 성공 - orderId: {}", kakaoPayData.getOrderId());
                                transactionService.completeCancel(kakaoPayData.getId());
                            },
                            () -> {
                                // 취소 API 호출 실패 (타임아웃 포함)
                                log.warn("카카오페이 취소 API 호출 실패 - orderId: {}, tid: {}. 취소 상태 조회 시도",
                                        kakaoPayData.getOrderId(), kakaoPayData.getTid());

                                // 타임아웃 가능성이 있으므로 취소 상태 조회 시도
                                Optional<KaKaoPayApproveDTO.KaKaoApiResponse> inquiryResult = kaKaoPayApiClient
                                        .inquirePaymentStatus(kakaoPayData.getTid());

                                if (inquiryResult.isPresent()) {
                                    // 조회 성공 - 실제 취소 상태 확인 필요
                                    // 카카오페이 응답에서 취소 여부를 확인할 수 있다면 처리
                                    // 현재는 조회만 하고 UNKNOWN 상태로 저장 (추후 수동 확인 필요)
                                    log.warn("취소 상태 조회 성공했으나 취소 여부 불명확 - orderId: {}, tid: {}. UNKNOWN 상태로 저장",
                                            kakaoPayData.getOrderId(), kakaoPayData.getTid());
                                    transactionService.updateStatus(kakaoPayData.getId(), PaymentStatus.UNKNOWN);
                                } else {
                                    // 조회도 실패 - 완전히 알 수 없는 상태
                                    log.error("취소 API 호출 및 상태 조회 모두 실패 - orderId: {}, tid: {}. UNKNOWN 상태로 저장",
                                            kakaoPayData.getOrderId(), kakaoPayData.getTid());
                                    transactionService.updateStatus(kakaoPayData.getId(), PaymentStatus.UNKNOWN);
                                }
                            });

            log.info("보상 트랜잭션 완료 - orderId: {}", kakaoPayData.getOrderId());

        } catch (Exception e) {
            log.error("보상 트랜잭션 처리 중 예외 발생 - orderId: {}, error: {}",
                    kakaoPayData.getOrderId(), e.getMessage(), e);

            // 예외 발생 시 UNKNOWN 상태로 저장 (재시도 안내 필요)
            try {
                transactionService.updateStatus(kakaoPayData.getId(), PaymentStatus.UNKNOWN);
                log.warn("보상 트랜잭션 예외 발생으로 UNKNOWN 상태 저장 - orderId: {}",
                        kakaoPayData.getOrderId());
            } catch (Exception ex) {
                log.error("상태 업데이트 실패 - orderId: {}", kakaoPayData.getOrderId(), ex);
            }
        }
    }
}
