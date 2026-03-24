package com.gdg.z_meet.domain.fcm.service.listener;

import com.gdg.z_meet.domain.fcm.event.FcmMessageEvent;
import com.gdg.z_meet.global.config.RabbitMqConfig;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.stereotype.Component;
import org.springframework.transaction.event.TransactionPhase;
import org.springframework.transaction.event.TransactionalEventListener;

/**
 * 트랜잭션 커밋 이후에 실제로 RabbitMQ로 메시지를 전송하는 리스너
 */
@Component
@RequiredArgsConstructor
@Slf4j
public class FcmMessageEventListener {

    private final RabbitTemplate rabbitTemplate;

    /**
     * phase = TransactionPhase.AFTER_COMMIT: 트랜잭션이 성공적으로 커밋된 후에 실행
     * fallbackExecution = true: 트랜잭션이 없는 환경에서 호출되어도 실행되도록 설정
     */
    @TransactionalEventListener(phase = TransactionPhase.AFTER_COMMIT, fallbackExecution = true)
    public void handleFcmMessageEvent(FcmMessageEvent event) {
        log.info("트랜잭션 커밋 감지 - RabbitMQ 메시지 전송 시작: messageId={}, routingKey={}",
                event.getMessage().getMessageId(), event.getRoutingKey());

        rabbitTemplate.convertAndSend(
                RabbitMqConfig.FCM_EXCHANGE,
                event.getRoutingKey(),
                event.getMessage());

        log.info("RabbitMQ 메시지 전송 완료: messageId={}", event.getMessage().getMessageId());
    }
}
