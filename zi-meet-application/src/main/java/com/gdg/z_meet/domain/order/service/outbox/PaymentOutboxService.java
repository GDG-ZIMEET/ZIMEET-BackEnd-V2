package com.gdg.z_meet.domain.order.service.outbox;

import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.global.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Profile("!local")
@RequiredArgsConstructor
public class PaymentOutboxService {

    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final RabbitTemplate rabbitTemplate;

    @Transactional
    public void publishEvent(KakaoPayData data) {
        try {
            KaKaoPayApproveDTO.Parameter parameter = KaKaoPayApproveDTO.Parameter.builder()
                    .orderId(data.getOrderId())
                    .userId(data.getBuyer().getId())
                    .pgToken(data.getPgToken())
                    .build();

            // RabbitMQ 전송
            rabbitTemplate.convertAndSend(
                    RabbitMqConfig.PAYMENT_EXCHANGE,
                    RabbitMqConfig.PAYMENT_APPROVE_ROUTING_KEY,
                    parameter);

            // 상태 업데이트
            data.markAsPublished();
            kakaoPayDataRepository.save(data);

            log.info("아웃박스 이벤트 발행 완료 - orderId: {}", data.getOrderId());

        } catch (Exception e) {
            log.error("아웃박스 이벤트 발행 실패 - orderId: {}, error: {}", data.getOrderId(), e.getMessage());
            data.increaseRetryCount();
            if (data.getPublishRetryCount() > 5) {
                data.markAsFailed();
            }
            kakaoPayDataRepository.save(data);
        }
    }
}