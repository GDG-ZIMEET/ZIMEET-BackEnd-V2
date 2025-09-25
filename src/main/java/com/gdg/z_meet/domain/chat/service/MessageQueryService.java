package com.gdg.z_meet.domain.chat.service;

import com.gdg.z_meet.domain.chat.dto.ChatMessageCacheDto;
import com.gdg.z_meet.domain.chat.dto.ChatMessageRes;
import com.gdg.z_meet.domain.chat.entity.Message;
import com.gdg.z_meet.domain.chat.entity.status.MessageType;
import com.gdg.z_meet.domain.chat.repository.JoinChatRepository;
import com.gdg.z_meet.domain.chat.repository.mongo.MessageRepository;
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

    private final ChatRedisService chatRedisService;
    private final ChatMongoService chatMongoService;
    private final JoinChatRepository joinChatRepository;
    private final UserRepository userRepository;

    /**
     * Look-Aside 전략 기반 메시지 조회
     */
    public List<ChatMessageRes> getMessagesByChatRoom(
            Long chatRoomId,
            Long userId,
            LocalDateTime lastMessageTime,
            int size
    ) {
        // 0) 채팅방 참여 여부 검증
//        if (!joinChatRepository.existsByUserIdAndChatRoomIdAndStatusActive(userId, chatRoomId)) {
//            throw new BusinessException(Code.JOINCHAT_NOT_FOUND);
//        }

        if (lastMessageTime == null) {
            lastMessageTime = LocalDateTime.now();
        }

        // 1) Redis 캐시 조회(Look-Aside 1차)
        List<ChatMessageRes> cachedMessages = chatRedisService.getMessages(chatRoomId, size);

        if (!cachedMessages.isEmpty()) {
            return cachedMessages;
        }

        // 2) Redis에 없으면 Mongo 조회
        List<Message> mongoMessages = chatMongoService.getMessages(chatRoomId, lastMessageTime, size);

        // 3) Mongo → DTO 변환 + Redis 캐싱
        List<ChatMessageRes> dtoList = mongoMessages.stream()
                .map(m -> {
                    User user = userRepository.findById(userId)
                            .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
                    return ChatMessageRes.builder()
                            .id(m.getMessageId())
                            .type(MessageType.CHAT)
                            .roomId(m.getChatRoomId())
                            .senderId(m.getUserId())
                            .senderName(user.getName())
                            .content(m.getContent())
                            .sendAt(m.getCreatedAt())
                            .emoji(user.getUserProfile().getEmoji())
                            .build();
                })
                .toList();

        dtoList.forEach(dto -> chatRedisService.saveToRedis(ChatMessageCacheDto.fromResponse(dto)));

        return dtoList;
    }

}