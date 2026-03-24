package com.gdg.z_meet.global.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.support.converter.Jackson2JsonMessageConverter;
import org.springframework.amqp.support.converter.MessageConverter;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class RabbitMqConfig {

    public static final String FCM_EXCHANGE = "fcm.exchange"; // 메시지 컨슈머 라우터

    // Broadcast (전체 발송)
    public static final String FCM_BROADCAST_QUEUE = "fcm.broadcast.queue";
    public static final String FCM_BROADCAST_ROUTING_KEY = "fcm.broadcast.send";

    // Single (단건/테스트 발송)
    public static final String FCM_SINGLE_QUEUE = "fcm.single.queue";
    public static final String FCM_SINGLE_ROUTING_KEY = "fcm.single.send";

    public static final String FCM_DLX_EXCHANGE = "fcm.dlx.exchange";
    public static final String FCM_DLQ_QUEUE = "fcm.dlq.queue";
    public static final String FCM_DLQ_ROUTING_KEY = "fcm.dlq";

    // Payment Approve (결제 승인 처리)
    public static final String PAYMENT_EXCHANGE = "payment.exchange";
    public static final String PAYMENT_APPROVE_QUEUE = "payment.approve.queue";
    public static final String PAYMENT_APPROVE_ROUTING_KEY = "payment.approve.request";

    /**
     * 라우팅 키가 정확히 일치해야 Queue 로 전달, 서버 재시작해도 큐 유지, 사용자 빠져나가도 큐 유지
     */
    @Bean
    public DirectExchange fcmExchange() {
        return new DirectExchange(FCM_EXCHANGE, true, false);
    }

    /**
     * Broadcast Queue
     */
    @Bean
    public Queue fcmBroadcastQueue() {
        return QueueBuilder.durable(FCM_BROADCAST_QUEUE)
                .withArgument("x-dead-letter-exchange", FCM_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FCM_DLQ_ROUTING_KEY)
                .withArgument("x-message-ttl", 300000) // 5분 TTL
                .build();
    }

    @Bean
    public Binding fcmBroadcastBinding() {
        return BindingBuilder.bind(fcmBroadcastQueue()).to(fcmExchange()).with(FCM_BROADCAST_ROUTING_KEY);
    }

    /**
     * Single Queue
     */
    @Bean
    public Queue fcmSingleQueue() {
        return QueueBuilder.durable(FCM_SINGLE_QUEUE)
                .withArgument("x-dead-letter-exchange", FCM_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FCM_DLQ_ROUTING_KEY)
                .withArgument("x-message-ttl", 300000) // 5분 TTL
                .build();
    }

    @Bean
    public Binding fcmSingleBinding() {
        return BindingBuilder.bind(fcmSingleQueue()).to(fcmExchange()).with(FCM_SINGLE_ROUTING_KEY);
    }

    /**
     * 실패하거나 만료된 메시지를 따로 보관하기 위한 구조
     */
    @Bean
    public DirectExchange fcmDlxExchange() {
        return new DirectExchange(FCM_DLX_EXCHANGE, true, false);
    }

    @Bean
    public Queue fcmDlqQueue() {
        return QueueBuilder.durable(FCM_DLQ_QUEUE).build();
    }

    @Bean
    public Binding fcmDlqBinding() {
        return BindingBuilder.bind(fcmDlqQueue()).to(fcmDlxExchange()).with(FCM_DLQ_ROUTING_KEY);
    }

    /**
     * Payment Approve Exchange
     */
    @Bean
    public DirectExchange paymentExchange() {
        return new DirectExchange(PAYMENT_EXCHANGE, true, false);
    }

    /**
     * Payment Approve Queue
     */
    @Bean
    public Queue paymentApproveQueue() {
        return QueueBuilder.durable(PAYMENT_APPROVE_QUEUE)
                .withArgument("x-dead-letter-exchange", FCM_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FCM_DLQ_ROUTING_KEY)
                .withArgument("x-message-ttl", 1800000) // 30분 TTL (결제 세션 고려)
                .build();
    }

    @Bean
    public Binding paymentApproveBinding() {
        return BindingBuilder.bind(paymentApproveQueue()).to(paymentExchange()).with(PAYMENT_APPROVE_ROUTING_KEY);
    }

    /**
     * JSON 메시지 변환기
     */
    @Bean
    public MessageConverter messageConverter() {
        Jackson2JsonMessageConverter converter = new Jackson2JsonMessageConverter();
        converter.setClassMapper(classMapper());
        return converter;
    }

    @Bean
    public org.springframework.amqp.support.converter.DefaultClassMapper classMapper() {
        org.springframework.amqp.support.converter.DefaultClassMapper classMapper = new org.springframework.amqp.support.converter.DefaultClassMapper();
        classMapper.setTrustedPackages("java.util", "java.lang", "com.gdg.z_meet.domain.fcm.dto",
                "com.gdg.z_meet.domain.order.dto");
        return classMapper;
    }

    /**
     * RabbitTemplate에 MessageConverter 설정
     */
    @Bean
    public RabbitTemplate rabbitTemplate(ConnectionFactory connectionFactory) {
        RabbitTemplate rabbitTemplate = new RabbitTemplate(connectionFactory);
        rabbitTemplate.setMessageConverter(messageConverter());
        return rabbitTemplate;
    }

    /**
     * 컨슈머 백프레셔 및 재시도 설정
     */
    @Bean
    public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
        SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
        factory.setConnectionFactory(connectionFactory);
        factory.setMessageConverter(messageConverter());

        // 백프레셔: 한 번에 가져올 메시지 수 제한 (DB 및 FCM API 부하 조절)
        factory.setPrefetchCount(20);

        // 재시도 정책 설정
        // 여기서 재시도는 'Consumer 애플리케이션 내'에서의 재시도입니다.
        // 최종 실패 시 DLQ로 보내기 위해 RejectAndDontRequeueRecoverer 사용 가능
        return factory;
    }
}
