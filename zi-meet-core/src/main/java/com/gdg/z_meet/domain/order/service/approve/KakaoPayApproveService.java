package com.gdg.z_meet.domain.order.service.approve;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.service.locking.KakaoPayLockService;
import com.gdg.z_meet.domain.order.service.recovery.PaymentRecoveryService;
import com.gdg.z_meet.domain.order.service.sync.PaymentSyncService;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPayApproveService {

    private final KaKaoPayApiClient kaKaoPayApiClient;
    private final KakaoPayLockService kakaoPayLockService;
    private final KakaoPayApproveTransactionService transactionService;
    private final PaymentRecoveryService paymentRecoveryService;
    private final PaymentSyncService paymentSyncService;

    /**
     * MQ를 이용한 비동기 결제 승인 요청
     */
    public KaKaoPayApproveDTO.Response approve(KaKaoPayApproveDTO.Parameter parameter, String idempotencyKey) {
        log.info("결제 승인 요청 MQ 적재 시작 - orderId: {}", parameter.getOrderId());

        // 1. 중복 처리 방어 및 상태 변경 (아웃박스 등록 포함)
        transactionService.startPaymentProcessing(parameter);

        log.info("결제 승인 요청 아웃박스 적재 완료 - orderId: {}", parameter.getOrderId());

        // 비동기 시점에는 정확한 승인 시각을 알 수 없으므로 현재 시간으로 가응답
        return KaKaoPayApproveDTO.Response.builder()
                .orderId(parameter.getOrderId())
                .approvedAt(java.time.LocalDateTime.now().toString())
                .build();
    }

    /**
     * 실제 결제 승인 로직 (워커에서 호출)
     */
    public KaKaoPayApproveDTO.Response processApproval(KaKaoPayApproveDTO.Parameter parameter) {
        try {
            // 락 획득 및 비즈니스 로직 실행 (워커에서 네임드락 수행)
            return kakaoPayLockService.executeWithLock(parameter.getOrderId(), () -> {

                // 1. 결제 데이터 조회 및 최종 상태 확인 (이미 처리 중이거나 완료되었는지)
                KakaoPayData kakaoPayData = transactionService.startPaymentProcessing(parameter);

                // 2. 외부 API 호출 (카카오페이 승인)
                Optional<KaKaoPayApproveDTO.KaKaoApiResponse> kakaoApiResponseOpt = kaKaoPayApiClient
                        .requestPaymentApprove(parameter, kakaoPayData);

                if (kakaoApiResponseOpt.isEmpty()) {
                    // 타임아웃 혹은 API 오류로 인해 응답이 없음 -> UNKNOWN 상태로 간주
                    log.warn("카카오페이 승인 응답 없음 (타임아웃 등) - orderId: {}. UNKNOWN 처리 시작", parameter.getOrderId());
                    handleUnknownPayment(parameter.getOrderId(), kakaoPayData.getTid());
                    throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);
                }

                KaKaoPayApproveDTO.KaKaoApiResponse kakaoApiResponse = kakaoApiResponseOpt.get();
                log.info("카카오페이 결제 최종 승인 성공 (워커) - orderId: {}", parameter.getOrderId());

                // 3. 결제 완료 처리 (상품 지급 및 구매 내역 생성)
                return transactionService.completePayment(
                        kakaoPayData.getId(), kakaoApiResponse, parameter);
            });
        } catch (BusinessException e) {
            log.error("워커 결제 승인 처리 실패 (비즈니스 예외) - orderId: {}, error: {}", parameter.getOrderId(), e.getMessage());
            // 이미 UNKNOWN 처리가 된 경우는 중복 호출 방지
            handlePaymentFailure(parameter.getOrderId(), "워커 승인 실패: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("워커 결제 승인 처리 실패 (예상치 못한 예외) - orderId: {}, error: {}", parameter.getOrderId(), e.getMessage(), e);
            handlePaymentFailure(parameter.getOrderId(), "워커 승인 실패: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 결제 결과를 알 수 없는 경우 (타임아웃 등) 처리
     * 즉시 동기화 시도 후, 여전히 실패 시 UNKNOWN 상태로 전환 및 보상 트랜잭션 예약
     */
    private void handleUnknownPayment(String orderId, String tid) {
        try {
            // 1. 즉시 상태 동기화 시도 (이미 승인되었을 가능성 확인)
            boolean synced = paymentSyncService.syncPaymentStatus(orderId);
            if (synced) {
                log.info("알 수 없는 상태에서 즉시 동기화 성공 - orderId: {}", orderId);
                return;
            }

            // 2. 여전히 알 수 없는 경우 UNKNOWN 상태로 변경
            var kakaoPayDataOpt = transactionService.findKakaoPayData(orderId);
            kakaoPayDataOpt.ifPresent(data -> {
                transactionService.updateStatus(data.getId(), PaymentStatus.UNKNOWN);
                log.warn("결제 상태를 UNKNOWN으로 변경 완료 (추후 스케줄러에서 재시도) - orderId: {}", orderId);
            });

            // 3. 보상 트랜잭션 예약 (망 취소 관점: 안정성을 위해 예약)
            paymentRecoveryService.scheduleRecovery(orderId, tid, "TIMEOUT_UNKNOWN");
        } catch (Exception e) {
            log.error("UNKNOWN 처리 중 오류 발생 - orderId: {}", orderId, e);
        }
    }

    /**
     * 결제 실패 시 보상 트랜잭션 예약 처리
     */
    private void handlePaymentFailure(String orderId, String errorMessage) {
        try {
            var kakaoPayDataOpt = transactionService.findKakaoPayData(orderId);
            if (kakaoPayDataOpt.isPresent()) {
                KakaoPayData data = kakaoPayDataOpt.get();
                // 이미 APPROVED나 UNKNOWN인 경우 보상 트랜잭션 예약을 신중히 결정
                if (data.getStatus() != PaymentStatus.APPROVED && data.getStatus() != PaymentStatus.UNKNOWN) {
                    paymentRecoveryService.scheduleRecovery(orderId, data.getTid(), errorMessage);
                }
            }
        } catch (Exception ex) {
            log.error("보상 트랜잭션 예약 중 오류 - orderId: {}", orderId, ex);
        }
    }
}
