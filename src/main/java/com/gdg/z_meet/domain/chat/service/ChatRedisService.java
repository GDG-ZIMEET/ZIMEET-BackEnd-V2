package com.gdg.z_meet.domain.chat.service;

import com.gdg.z_meet.domain.chat.dto.ChatMessageCacheDto;
import com.gdg.z_meet.domain.chat.dto.ChatMessageReq;
import com.gdg.z_meet.domain.chat.dto.ChatMessageRes;
import com.gdg.z_meet.domain.chat.redis.RedisPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
public class ChatRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisPublisher redisPublisher;

    private static final int MAX_REDIS_MESSAGES = 300;

    // 메시지 발행 + Redis 저장 (Write-Through에서 Mongo 저장은 외부에서 함께 수행)
    public void publishAndCache(ChatMessageReq messageDto) {
        redisPublisher.publishToRoom(messageDto.getRoomId(), messageDto);

        ChatMessageCacheDto cacheDto = ChatMessageCacheDto.fromReq(messageDto);

        saveToRedis(cacheDto);
    }

    // Redis에 메시지 저장
    public void saveToRedis(ChatMessageCacheDto messageDto) {
        String key = "chatroom:" + messageDto.getRoomId() + ":messages";

        redisTemplate.opsForList().rightPush(key, messageDto);
        redisTemplate.opsForValue().set("chatroom:" + messageDto.getRoomId() + ":latestMessage", messageDto.getContent());
        redisTemplate.opsForValue().set("chatroom:" + messageDto.getRoomId() + ":latestMessageTime", LocalDateTime.now().toString());

        trimMessages(key);
    }

    // Redis 메시지 개수 제한
    private void trimMessages(String key) {
        Long totalMessages = redisTemplate.opsForList().size(key);
        if (totalMessages != null && totalMessages > MAX_REDIS_MESSAGES) {
            redisTemplate.opsForList().trim(key, -MAX_REDIS_MESSAGES, -1);
        }
    }

    // Look-Aside: Redis에서 최근 메시지 조회
    public List<ChatMessageRes> getMessages(Long roomId, int size) {
        String key = "chatroom:" + roomId + ":messages";
        List<Object> messages = redisTemplate.opsForList().range(key, -size, -1);
        if (messages == null) return List.of();
        return messages.stream().map(obj -> (ChatMessageRes)obj).toList();
    }

//    // 메시지 발행
//    public void publishMessage(ChatMessageDto messageDto) {
//        redisPublisher.publishToRoom(messageDto.getRoomId(), messageDto);
//        saveToRedis(messageDto);
//    }
//
//    // Redis에 메시지 저장 및 최신 메시지,시간 업데이트
//    public void saveToRedis(ChatMessageDto messageDto) {
//        String key = "chatroom:" + messageDto.getRoomId() + ":messages";
//
//        List<Object> recentMessages = redisTemplate.opsForList().range(key, -MAX_REDIS_MESSAGES, -1);
//        boolean isDuplicate = recentMessages != null && recentMessages.stream()
//                .anyMatch(obj -> ((ChatMessageDto)obj).getId().equals(messageDto.getId()));
//
//        if (!isDuplicate) {
//            redisTemplate.opsForList().rightPush(key, messageDto);
//            redisTemplate.opsForValue().set("chatroom:" + messageDto.getRoomId() + ":latestMessage", messageDto.getContent());
//            redisTemplate.opsForValue().set("chatroom:" + messageDto.getRoomId() + ":latestMessageTime", LocalDateTime.now().toString());
//        }
//
//        // 메시지 개수 제한 (trim)
//        trimMessages(key);
//    }
//
//    // Redis 메시지 개수 제한
//    private void trimMessages(String key) {
//        Long totalMessages = redisTemplate.opsForList().size(key);
//        if (totalMessages != null && totalMessages > MAX_REDIS_MESSAGES) {
//            redisTemplate.opsForList().trim(key, -MAX_REDIS_MESSAGES, -1);
//            //log.info("Redis 메시지 trim 완료 - key: {}, 유지 개수: {}", key, MAX_REDIS_MESSAGES);
//        }
//    }
//
//    public List<ChatMessageDto> getMessages(Long roomId, int size) {
//        String key = "chatroom:" + roomId + ":messages";
//        List<Object> messages = redisTemplate.opsForList().range(key, -size, -1);
//        if (messages == null) return List.of();
//        return messages.stream().map(obj -> (ChatMessageDto)obj).toList();
//    }

//    public List<ChatMessageDto> getMessagesBefore(Long roomId, LocalDateTime before, int limit) {
//        String key = "chatroom:" + roomId + ":messages";
//        List<Object> raw = redisTemplate.opsForList().range(key, 0, -1);
//        if (raw == null) return List.of();
//
//        return raw.stream()
//                .map(obj -> (ChatMessageDto) obj)
//                .filter(m -> m.getSendAt() != null && m.getSendAt().isBefore(before))
//                .sorted(Comparator.comparing(ChatMessageDto::getSendAt).reversed())
//                .limit(limit)
//                .toList();
//    }
}
