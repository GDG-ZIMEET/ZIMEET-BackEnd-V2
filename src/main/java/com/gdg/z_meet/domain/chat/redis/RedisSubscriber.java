package com.gdg.z_meet.domain.chat.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdg.z_meet.domain.chat.dto.ChatMessageReq;
import com.gdg.z_meet.domain.chat.dto.ChatMessageRes;
import com.gdg.z_meet.domain.chat.service.ChatMessageHandlerService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.messaging.simp.SimpMessageSendingOperations;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper;
    private final ChatMessageHandlerService chatMessageHandlerService;
    private final SimpMessageSendingOperations messagingTemplate;

    // Redis에서 메시지를 수신 시 호출되는 메서드
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String msgBody = new String(message.getBody(), StandardCharsets.UTF_8);
            String channel = new String(message.getChannel(), StandardCharsets.UTF_8);
            ChatMessageReq chatMessageReq = objectMapper.readValue(msgBody, ChatMessageReq.class);
            
            log.info("Redis 메시지 수신 - Channel: {}, Message: {}", channel, msgBody);
            
            // 채팅 메시지 채널인 경우에만 처리
//            if ("chat:room".equals(channel)) {
//                ChatMessageDto chatMessageDto = objectMapper.readValue(msgBody, ChatMessageDto.class);
//                chatMessageHandlerService.handleMessage(chatMessageDto);
//            }

            messagingTemplate.convertAndSend("/topic/" + chatMessageReq.getRoomId(), chatMessageReq);
            
        } catch (Exception e) {
            log.error("Redis 구독 메시지 처리 실패", e);
        }
    }

}
