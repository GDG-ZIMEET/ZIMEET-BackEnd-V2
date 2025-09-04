package com.gdg.z_meet.domain.chat.service;

import com.gdg.z_meet.domain.chat.dto.ChatMessage;
import com.gdg.z_meet.domain.chat.entity.Message;
import com.gdg.z_meet.domain.chat.entity.status.MessageType;
import com.gdg.z_meet.domain.chat.repository.JoinChatRepository;
import com.gdg.z_meet.domain.chat.repository.mongo.MongoMessageRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class MessageQueryService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final MongoMessageRepository mongoMessageRepository;
    private final JoinChatRepository joinChatRepository;
    private final UserRepository userRepository;

    private static final String CHAT_ROOM_MESSAGES_KEY = "chatroom:%s:messages";

    public List<ChatMessage> getMessagesByChatRoom(Long chatRoomId, Long userId, LocalDateTime lastMessageTime, int size) {
        if (!joinChatRepository.existsByUserIdAndChatRoomIdAndStatusActive(userId, chatRoomId)) {
            throw new BusinessException(Code.JOINCHAT_NOT_FOUND);
        }

        if (lastMessageTime == null) {
            lastMessageTime = LocalDateTime.now();
        }

        // ✅ LocalDateTime → UTC Date 변환
        Instant instant = lastMessageTime.atZone(ZoneId.systemDefault()).toInstant(); // 시스템 시간대 → UTC
        Date utcDate = Date.from(instant); // MongoDB 비교용

        String redisKey = String.format("chatroom:%s:messages", chatRoomId);
        List<Object> redisRaw = redisTemplate.opsForList().range(redisKey, 0, -1);

        LocalDateTime finalLastMessageTime = lastMessageTime;
        List<ChatMessage> redisMessages = Optional.ofNullable(redisRaw).orElse(List.of()).stream()
                .map(obj -> (ChatMessage) obj)
                .filter(m -> m.getSendAt() != null && m.getSendAt().isBefore(finalLastMessageTime))
                .sorted(Comparator.comparing(ChatMessage::getSendAt).reversed())
                .limit(size)
                .collect(Collectors.toList());

        int fetched = redisMessages.size();

        if (fetched < size) {
            int remaining = size - fetched;
            Pageable pageable = PageRequest.of(0, remaining, Sort.by("createdAt").descending());

            List<Message> dbMessages = mongoMessageRepository.findByChatRoomIdAndCreatedAtBefore(
                    chatRoomId.toString(), utcDate, pageable
            );

            Set<String> redisIds = redisMessages.stream()
                    .map(ChatMessage::getId)
                    .collect(Collectors.toSet());

            List<ChatMessage> dbChatMessages = dbMessages.stream()
                    .filter(m -> m.getMessageId() != null && !redisIds.contains(m.getMessageId()))
                    .map(m -> {
                        User user = userRepository.findById(Long.parseLong(m.getUserId()))
                                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
                        return ChatMessage.builder()
                                .id(m.getMessageId())
                                .type(MessageType.CHAT)
                                .roomId(Long.parseLong(m.getChatRoomId()))
                                .senderId(Long.parseLong(m.getUserId()))
                                .senderName(user.getName())
                                .content(m.getContent())
                                .sendAt(m.getCreatedAt())
                                .emoji(user.getUserProfile().getEmoji())
                                .build();
                    })
                    .collect(Collectors.toList());

            redisMessages.addAll(dbChatMessages);
        }

        return redisMessages.stream()
                .sorted(Comparator.comparing(ChatMessage::getSendAt).reversed())
                .collect(Collectors.toList());
    }

}