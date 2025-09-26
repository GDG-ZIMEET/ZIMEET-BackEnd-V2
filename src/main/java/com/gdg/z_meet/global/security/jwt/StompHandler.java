package com.gdg.z_meet.global.security.jwt;

import io.jsonwebtoken.Claims;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.HttpHeaders;
import org.springframework.messaging.Message;
import org.springframework.messaging.MessageChannel;
import org.springframework.messaging.simp.stomp.StompCommand;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.messaging.support.ChannelInterceptor;
import org.springframework.messaging.support.MessageHeaderAccessor;
import org.springframework.stereotype.Component;
import org.springframework.util.StringUtils;

import java.security.Principal;

@Slf4j
@Component
@RequiredArgsConstructor
public class StompHandler implements ChannelInterceptor {

    private final JwtUtil jwtUtil;
//    private final ChatRoomService chatRoomService;

    @Override
    public Message<?> preSend(Message<?> message, MessageChannel channel) {

//        StompHeaderAccessor accessor = StompHeaderAccessor.wrap(message); -> 새로운 accessor가 생기며 user정보 사라짐

        StompHeaderAccessor accessor = MessageHeaderAccessor
                .getAccessor(message, StompHeaderAccessor.class);

        StompCommand command = accessor.getCommand();

        if (StompCommand.CONNECT.equals(command)) {
            // WebSocket 연결 시 Authorization 헤더에서 JWT 추출
            String token = accessor.getFirstNativeHeader(HttpHeaders.AUTHORIZATION);

            if (!StringUtils.hasText(token) || !token.startsWith("Bearer ")) {
                log.warn("토큰이 누락되었거나 형식이 잘못되었습니다.");
                throw new IllegalArgumentException("JWT 토큰 누락 또는 형식 오류");
//                return null; //예외 반환시 웹소켓 연결 해제되는 현상 방지위해 null 반환
            }

            token = token.substring(7); // "Bearer " 제거

            if (!jwtUtil.validateToken(token)) {
                log.warn("유효하지 않은 JWT 토큰입니다.");
                throw new IllegalArgumentException("유효하지 않은 JWT 토큰");
//                return null;
            }

            // 토큰 유효성 검증 성공 시, 사용자 정보 추출 후 Principal 설정
            String studentNumber = jwtUtil.getStudentNumberFromToken(token);
            accessor.setUser(() -> studentNumber); // Principal 설정

            //사용자 정보를 websocket 연결 컨텍스트에 저장 (이후 subscribe, send 시에도 꺼내 쓸 수 있게)
            //accessor.getSessionAttributes().put("studentNumber", studentNumber);

            log.info("stomp 연결 성공: {}", studentNumber);
        }

        if (StompCommand.SUBSCRIBE.equals(command)) {

            Principal user = accessor.getUser();

            if (user == null) {
                log.warn("SUBSCRIBE 요청에 인증된 사용자 없음");
                return null; // 예외 대신 null 반환 시 연결 해제 방지
            }

//            Object user = accessor.getSessionAttributes().get("studentNumber");
//            if (user != null) {
//                accessor.setUser(() -> (String) user);
//            } else {
//                log.warn("SUBSCRIBE 요청에 인증된 사용자 없음");
//                return null;
//            }

            String studentNumber = accessor.getUser().getName(); // CONNECT 시 저장한 Principal (학번)
            String destination = accessor.getDestination();  // 예: /sub/chat/room/3
            Long chatRoomId = extractRoomIdFromDestination(destination);

            //chatRoomService에서 hasAccess 메서드로 권한 확인 추가 예정

//            if (!chatRoomService.hasAccess(email, chatRoomId)) {
//                log.warn("채팅방 접근 권한 없음 - userEmail: {}, roomId: {}", email, chatRoomId);
//                //throw new AccessDeniedException("채팅방 접근 권한 없음");
//                return null;  //예외 반환시 웹소켓 연결 해제되는 현상 방지위해 null 반환
//            }

            log.info("채팅방 구독 허용 - studentNumber: {}, roomId: {}", studentNumber, chatRoomId);
        }

        if (StompCommand.DISCONNECT.equals(command)) {
            // 연결 해제 시 로그
            log.info("stomp 연결 해제");
        }

        return message;


    }

    private Long extractRoomIdFromDestination(String destination) {
        // 예: /sub/chat/room/3 → 3
        if (destination == null) {
            return null;
        }
        String[] parts = destination.split("/");
        try {
            return Long.parseLong(parts[parts.length - 1]);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("채팅방 ID 파싱 실패: " + destination);
        }
    }

}
