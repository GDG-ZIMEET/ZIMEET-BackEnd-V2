package com.gdg.z_meet.domain.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdg.z_meet.domain.chat.dto.ChatMessageCacheDto;
import com.gdg.z_meet.domain.chat.dto.ChatMessageReq;
import com.gdg.z_meet.domain.chat.dto.ChatMessageRes;
import com.gdg.z_meet.domain.chat.redis.RedisPublisher;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRedisService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final RedisPublisher redisPublisher;
    private final ObjectMapper objectMapper;

    private static final int MAX_REDIS_MESSAGES = 300;

    // 메시지 발행 + Redis 저장 (Write-Through에서 Mongo 저장은 외부에서 함께 수행)
    public void publishAndCache(Long roomId, Long userId, ChatMessageReq messageDto) {
        ChatMessageCacheDto cacheDto = ChatMessageCacheDto.fromReq(roomId, userId, messageDto);

        redisPublisher.publishToRoom(roomId, cacheDto);

        saveToRedis(roomId, cacheDto);
    }

//    // Redis에 메시지 저장
//    public void saveToRedis(ChatMessageCacheDto messageDto) {
//        String key = "chatroom:" + messageDto.getRoomId() + ":messages";
//
//        redisTemplate.opsForList().rightPush(key, messageDto);
//        redisTemplate.opsForValue().set("chatroom:" + messageDto.getRoomId() + ":latestMessage", messageDto.getContent());
//        redisTemplate.opsForValue().set("chatroom:" + messageDto.getRoomId() + ":latestMessageTime", LocalDateTime.now().toString());
//
//        trimMessages(key);
//
//        // TTL 설정
//        redisTemplate.expire(key, Duration.ofDays(7));
//        redisTemplate.expire("chatroom:" + messageDto.getRoomId() + ":latestMessage", Duration.ofDays(7));
//        redisTemplate.expire("chatroom:" + messageDto.getRoomId() + ":latestMessageTime", Duration.ofDays(7));
//    }
//
//    // Redis 메시지 개수 제한
//    private void trimMessages(String key) {
//        Long totalMessages = redisTemplate.opsForList().size(key);
//        if (totalMessages != null && totalMessages > MAX_REDIS_MESSAGES) {
//            redisTemplate.opsForList().trim(key, -MAX_REDIS_MESSAGES, -1); //최근 300개만 남기고 오래된 메시지들 삭제
//        }
//    }
//
//    // Look-Aside: Redis에서 최근 메시지 조회
//    public List<ChatMessageRes> getMessages(Long roomId, int size) {
//        String key = "chatroom:" + roomId + ":messages";
//        List<Object> messages = redisTemplate.opsForList().range(key, -size, -1);
//        if (messages == null) return List.of();
//
//        return messages.stream()
//                .map(obj -> objectMapper.convertValue(obj, ChatMessageCacheDto.class)) // LinkedHashMap → DTO 변환
//                .map(ChatMessageCacheDto::toResponse)
//                .toList();
//    }


    public void saveToRedis(Long roomId, ChatMessageCacheDto messageDto) {
        String key = "chatroom:" + roomId + ":messages";

        // 메시지를 ZSet에 저장, score = timestamp (밀리초)
        double score = messageDto.getSendAt().atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        redisTemplate.opsForZSet().add(key, messageDto, score);

        // 최신 메시지 별도 저장
        redisTemplate.opsForValue().set("chatroom:" + roomId + ":latestMessage", messageDto.getContent());
        redisTemplate.opsForValue().set("chatroom:" + roomId + ":latestMessageTime", LocalDateTime.now().toString());

        trimMessages(key);

        // TTL 설정
        redisTemplate.expire(key, Duration.ofDays(7));
        redisTemplate.expire("chatroom:" + roomId + ":latestMessage", Duration.ofDays(7));
        redisTemplate.expire("chatroom:" + roomId + ":latestMessageTime", Duration.ofDays(7));
    }

    // Redis 메시지 개수 제한 (최근 MAX_REDIS_MESSAGES개만 남기기)
    private void trimMessages(String key) {
        Long totalMessages = redisTemplate.opsForZSet().zCard(key);
        if (totalMessages != null && totalMessages > MAX_REDIS_MESSAGES) {
            // 오래된 메시지 삭제
            redisTemplate.opsForZSet().removeRange(key, 0, totalMessages - MAX_REDIS_MESSAGES - 1);
        }
    }


    public List<ChatMessageRes> getMessages(Long roomId, LocalDateTime lastMessageTime, int size) {
        String key = "chatroom:" + roomId + ":messages";

        // 1) ZSet 조회
        Set<Object> rawMessages;
        if (lastMessageTime == null) {
            rawMessages = redisTemplate.opsForZSet().reverseRange(key, 0, size - 1);
        } else {
            double score = lastMessageTime.atZone(ZoneId.systemDefault()).toInstant().minusMillis(1).toEpochMilli();
            rawMessages = redisTemplate.opsForZSet().reverseRangeByScore(key, Double.NEGATIVE_INFINITY, score, 0, size);
        }

        if (rawMessages == null || rawMessages.isEmpty()) return new ArrayList<>();

        // 2) Object → ChatMessageCacheDto 변환 (redistemplate이 zset에서 가져올 때 제네릭 타입이 아닌 Object로 가져옴)
        List<ChatMessageCacheDto> messages = rawMessages.stream()
                .map(obj -> objectMapper.convertValue(obj, ChatMessageCacheDto.class))
                .toList();

        // 3) DTO → Response 변환 및 가변 리스트 반환
        return messages.stream()
                .map(ChatMessageCacheDto::toResponse)
                .collect(Collectors.toCollection(ArrayList::new));
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
