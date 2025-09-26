package com.gdg.z_meet.domain.chat.controller;


import com.gdg.z_meet.domain.chat.dto.ChatMessageReq;
import com.gdg.z_meet.domain.chat.dto.ChatMessageRes;
import com.gdg.z_meet.domain.chat.service.ChatMessageHandlerService;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.Message;
import org.springframework.messaging.handler.annotation.DestinationVariable;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.messaging.simp.stomp.StompHeaderAccessor;
import org.springframework.stereotype.Controller;

import java.security.Principal;

@Controller
@RequiredArgsConstructor
public class ChatWebSocketController {

    private final ChatMessageHandlerService chatMessageHandlerService;

    // prefix를 "/app"로 설정했으므로, 실제 메시지는 "/app/chat/{roomId}"로 전송됨
    @MessageMapping("/chat/{roomId}")
    public void handleMessage(@DestinationVariable Long roomId,
                              ChatMessageReq chatMessageReq,
                              Principal principal) {
        Long userId = Long.parseLong(principal.getName());

        if(principal == null) {
            throw new RuntimeException("사용자 정보 없음");
        }

        // 채팅 메시지 전송
        chatMessageHandlerService.sendMessage(roomId, userId, chatMessageReq);
    }
}
