package com.gdg.z_meet.domain.order.service.approve;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;

import com.gdg.z_meet.domain.order.service.locking.KakaoPayLockService;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPayApproveService {

    private final KaKaoPayApiClient kaKaoPayApiClient;
    private final KakaoPayLockService kakaoPayLockService;
    private final KakaoPayApproveTransactionService transactionService;
    private final com.gdg.z_meet.domain.order.service.recovery.PaymentRecoveryService paymentRecoveryService;
    private final org.springframework.amqp.rabbit.core.RabbitTemplate rabbitTemplate;

    /**
     * MQ를 이용한 비동기 결제 승인 요청
     */
    public KaKaoPayApproveDTO.Response approve(KaKaoPayApproveDTO.Parameter parameter, String idempotencyKey) {
        log.info("결제 승인 요청 MQ 적재 시작 - orderId: {}", parameter.getOrderId());

        // 1. 중복 처리 방어 및 상태 변경 (API 서버에서 조기 차단)
        // startPaymentProcessing은 REQUIRES_NEW 트랜잭션으로 상태를 PROCESSING으로 변경함
        transactionService.startPaymentProcessing(parameter);

        // 2. MQ 메시지 전송
        rabbitTemplate.convertAndSend(
                com.gdg.z_meet.global.config.RabbitMqConfig.PAYMENT_EXCHANGE,
                com.gdg.z_meet.global.config.RabbitMqConfig.PAYMENT_APPROVE_ROUTING_KEY,
                parameter);

        log.info("결제 승인 요청 MQ 적재 완료 - orderId: {}", parameter.getOrderId());

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

                // 만약 이미 APPROVED 상태라면 위 메서드에서 예외가 발생함

                // 2. 외부 API 호출 (카카오페이 승인)
                KaKaoPayApproveDTO.KaKaoApiResponse kakaoApiResponse = callKakaoPayApproveApi(parameter, kakaoPayData);

                log.info("카카오페이 결제 최종 승인 성공 (워커) - orderId: {}", parameter.getOrderId());

                // 3. 결제 완료 처리 (상품 지급 및 구매 내역 생성)
                return transactionService.completePayment(
                        kakaoPayData.getId(), kakaoApiResponse, parameter);
            });
        } catch (BusinessException e) {
            log.error("워커 결제 승인 처리 실패 (비즈니스 예외) - orderId: {}, error: {}", parameter.getOrderId(), e.getMessage());
            handlePaymentFailure(parameter.getOrderId(), "워커 승인 실패: " + e.getMessage());
            throw e;
        } catch (Exception e) {
            log.error("워커 결제 승인 처리 실패 (예상치 못한 예외) - orderId: {}, error: {}", parameter.getOrderId(), e.getMessage(), e);
            handlePaymentFailure(parameter.getOrderId(), "워커 승인 실패: " + e.getMessage());
            throw e;
        }
    }

    /**
     * 결제 실패 시 보상 트랜잭션 예약 처리
     */
    private void handlePaymentFailure(String orderId, String errorMessage) {
        try {
            var kakaoPayDataOpt = transactionService.findKakaoPayData(orderId);
            String tid = kakaoPayDataOpt.map(KakaoPayData::getTid).orElse(null);

            paymentRecoveryService.scheduleRecovery(orderId, tid, errorMessage);
        } catch (Exception ex) {
            log.error("보상 트랜잭션 예약 중 오류 - orderId: {}", orderId, ex);
        }
    }

    /**
     * 카카오페이 승인 API 호출
     */
    private KaKaoPayApproveDTO.KaKaoApiResponse callKakaoPayApproveApi(
            KaKaoPayApproveDTO.Parameter parameter, KakaoPayData kakaoPayData) {
        return kaKaoPayApiClient
                .requestPaymentApprove(parameter, kakaoPayData)
                .orElseThrow(() -> new BusinessException(Code.INVALID_KAKAO_API_RESPONSE));
    }
}