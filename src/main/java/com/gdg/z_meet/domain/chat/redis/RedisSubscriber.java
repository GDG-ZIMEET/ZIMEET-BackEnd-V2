package com.gdg.z_meet.domain.chat.redis;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gdg.z_meet.domain.chat.dto.ChatMessage;
import com.gdg.z_meet.domain.chat.service.ChatMessageService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.connection.Message;
import org.springframework.data.redis.connection.MessageListener;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;

@Slf4j
@Component
@RequiredArgsConstructor
public class RedisSubscriber implements MessageListener {

    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
    private final ChatMessageService chatMessageService;

    // Redis에서 메시지를 수신 시 호출되는 메서드
    @Override
    public void onMessage(Message message, byte[] pattern) {
        try {
            String msgBody = new String(message.getBody(), StandardCharsets.UTF_8);
            String channel = new String(pattern, StandardCharsets.UTF_8);
            
            log.info("Redis 메시지 수신 - Channel: {}, Message: {}", channel, msgBody);
            
            // 채팅 메시지 채널인 경우에만 처리
            if ("chat:message".equals(channel)) {
                ChatMessage chatMessage = objectMapper.readValue(msgBody, ChatMessage.class);
                chatMessageService.handleReceivedMessage(chatMessage);
            }
            
        } catch (Exception e) {
            log.error("Redis 구독 메시지 처리 실패", e);
        }
    }

}
