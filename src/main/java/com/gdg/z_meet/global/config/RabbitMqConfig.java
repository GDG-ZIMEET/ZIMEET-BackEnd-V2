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
    
    public static final String FCM_EXCHANGE = "fcm.exchange";      // 메시지 컨슈머 라우터
    public static final String FCM_QUEUE = "fcm.queue";            // 메시지 적재 큐
    public static final String FCM_ROUTING_KEY = "fcm.send";       // Queue 로 메시지를 보내는데 쓰이는 키
    
    public static final String FCM_DLX_EXCHANGE = "fcm.dlx.exchange";
    public static final String FCM_DLQ_QUEUE = "fcm.dlq.queue";
    public static final String FCM_DLQ_ROUTING_KEY = "fcm.dlq";


    /**
     *  라우팅 키가 정확히 일치해야 Queue 로 전달, 서버 재시작해도 큐 유지, 사용자 빠져나가도 큐 유지
     */
    @Bean
    public DirectExchange fcmExchange() {
        return new DirectExchange(FCM_EXCHANGE, true, false);
    }

    /**
     *  발행된 메시지가 TTL 을 초과하면, Dead Letter 처리
     */
    @Bean
    public Queue fcmQueue() {
        return QueueBuilder.durable(FCM_QUEUE)
                .withArgument("x-dead-letter-exchange", FCM_DLX_EXCHANGE)
                .withArgument("x-dead-letter-routing-key", FCM_DLQ_ROUTING_KEY)
                .withArgument("x-message-ttl", 300000) // 5분 TTL
                .build();
    }

    @Bean
    public Binding fcmBinding() {
        return BindingBuilder.bind(fcmQueue()).to(fcmExchange()).with(FCM_ROUTING_KEY);
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
        org.springframework.amqp.support.converter.DefaultClassMapper classMapper = 
            new org.springframework.amqp.support.converter.DefaultClassMapper();
        classMapper.setTrustedPackages("com.gdg.z_meet.*");
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
