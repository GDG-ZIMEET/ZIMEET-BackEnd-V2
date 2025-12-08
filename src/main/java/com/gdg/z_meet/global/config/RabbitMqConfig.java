package com.gdg.z_meet.global.config;

import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.QueueBuilder;
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
        classMapper.setTrustedPackages("java.util", "java.lang", "com.gdg.z_meet.domain.fcm.dto");
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
}
