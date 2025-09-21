package com.gdg.z_meet.global.config;

import com.gdg.z_meet.global.security.jwt.StompHandler;
import lombok.RequiredArgsConstructor;
import org.springframework.context.annotation.Configuration;
import org.springframework.messaging.simp.config.ChannelRegistration;
import org.springframework.messaging.simp.config.MessageBrokerRegistry;
import org.springframework.web.socket.config.annotation.EnableWebSocketMessageBroker;
import org.springframework.web.socket.config.annotation.StompEndpointRegistry;
import org.springframework.web.socket.config.annotation.WebSocketMessageBrokerConfigurer;

@Configuration
@EnableWebSocketMessageBroker
@RequiredArgsConstructor
public class WebSocketConfig implements WebSocketMessageBrokerConfigurer {

    private final StompHandler stompHandler;

    @Override
    public void registerStompEndpoints(StompEndpointRegistry registry) {
        registry.addEndpoint("/ws") // WebSocket 연결 URL
                .setAllowedOriginPatterns("*")
                .withSockJS() // SockJS 지원
                .setWebSocketEnabled(true)
                .setSessionCookieNeeded(false);

        registry.addEndpoint("/ws/plain") // 일반 WebSocket 전용 (Postman 테스트용)
                .setAllowedOriginPatterns("*");
    }

    @Override
    public void configureMessageBroker(MessageBrokerRegistry config) {
        //메시지를 받을 때 경로를 설정
        //해당 경로로 simpleBroker 등록
        //구독하는 클라이언트에게 메시지 전달
        config.enableSimpleBroker("/topic", "/queue");

        //메시지 전송 시 경로 설정 / send 요청 처리
        //클라이언트가 메시지를 보낼 때, 경로 앞에 /pub 있으면 broker로 보내짐
        config.setApplicationDestinationPrefixes("/app");

        //사용자별 프라이빗 경로 지정
        config.setUserDestinationPrefix("/user");
    }

    @Override
    public void configureClientInboundChannel(ChannelRegistration registration) {
        registration.interceptors(stompHandler);
    }
}
