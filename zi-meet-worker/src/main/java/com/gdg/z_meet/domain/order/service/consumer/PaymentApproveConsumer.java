package com.gdg.z_meet.domain.order.service.consumer;

import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.service.approve.KakaoPayApproveService;
import com.gdg.z_meet.global.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class PaymentApproveConsumer {

    private final KakaoPayApproveService kakaoPayApproveService;

    @RabbitListener(queues = RabbitMqConfig.PAYMENT_APPROVE_QUEUE, containerFactory = "rabbitListenerContainerFactory")
    public void consumeApproveRequest(KaKaoPayApproveDTO.Parameter parameter) {
        log.info("결제 승인 요청 메시지 수신 - orderId: {}", parameter.getOrderId());

        try {
            kakaoPayApproveService.processApproval(parameter);
            log.info("결제 승인 처리 성공 - orderId: {}", parameter.getOrderId());
        } catch (Exception e) {
            log.error("결제 승인 처리 중 에러 발생 - orderId: {}, error: {}", parameter.getOrderId(), e.getMessage());
            // 여기서 예외를 던지면 RabbitMQ 재시도 정책에 의해 재시도하거나 DLQ로 이동합니다.
            throw e;
        }
    }
}