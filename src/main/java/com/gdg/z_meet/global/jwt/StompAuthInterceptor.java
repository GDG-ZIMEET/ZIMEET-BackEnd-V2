package com.gdg.z_meet.global.jwt;

import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.stereotype.Component;

@Component
public class StompAuthInterceptor implements ChannelInterceptor {

    private final JwtUtil jwtUtil;

    public StompAuthInterceptor(JwtUtil jwtUtil) {
        this.jwtUtil = jwtUtil;
    }

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {
        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message);

        if (StompCommand.CONNECT.equals(accessor.getCommand())) {
            String token = accessor.getFirstNativeHeader("Authorization");

            if (token == null) {
                token = accessor.getFirstNativeHeader("authorization");
            }

            if (token == null) {
                throw new IllegalArgumentException("Missing Token");
            }

            // ✅ "Bearer " 제거 후 검증
            if (token.startsWith("Bearer ")) {
                token = token.substring(7);
            }
//
//            System.out.println("token:" + token);

            // 사용자 정보 추출 후 Principal 설정
            String studentNumber = jwtUtil.getStudentNumberFromToken(token);
//
//            System.out.println("stuNumber:" + studentNumber);
            accessor.setUser(() -> studentNumber);
        }

        return message;
    }
}

