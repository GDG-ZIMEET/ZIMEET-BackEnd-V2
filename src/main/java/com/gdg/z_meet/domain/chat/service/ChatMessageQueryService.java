package com.gdg.z_meet.domain.chat.service;

import com.gdg.z_meet.domain.chat.dto.ChatMessageCacheDto;
import com.gdg.z_meet.domain.chat.dto.ChatMessageRes;
import com.gdg.z_meet.domain.chat.entity.Message;
import com.gdg.z_meet.domain.chat.entity.status.MessageType;
import com.gdg.z_meet.domain.chat.repository.JoinChatRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Slf4j
public class ChatMessageQueryService {

    private final ChatRedisService chatRedisService;
    private final ChatMongoService chatMongoService;
    private final JoinChatRepository joinChatRepository;
    private final UserRepository userRepository;

//    /**
//     * Look-Aside 전략 기반 메시지 조회
//     */
//    public List<ChatMessageRes> getMessagesByChatRoom(
//            Long chatRoomId,
//            Long userId,
//            LocalDateTime lastMessageTime,
//            int size
//    ) {
//        // 0) 채팅방 참여 여부 검증
////        if (!joinChatRepository.existsByUserIdAndChatRoomIdAndStatusActive(userId, chatRoomId)) {
////            throw new BusinessException(Code.JOINCHAT_NOT_FOUND);
////        }
//
//        if (lastMessageTime == null) {
//            lastMessageTime = LocalDateTime.now();
//        }
//
//        // 1) Redis 캐시 조회(Look-Aside 1차)
//        List<ChatMessageRes> cachedMessages = chatRedisService.getMessages(chatRoomId, size);
//
//        if (!cachedMessages.isEmpty()) {
//            return cachedMessages;
//        }
//
//        // 2) Redis에 없으면 Mongo 조회
//        List<Message> mongoMessages = chatMongoService.getMessages(chatRoomId, lastMessageTime, size);
//
//        // 3) Mongo → DTO 변환 + Redis 캐싱
//        List<ChatMessageRes> dtoList = mongoMessages.stream()
//                .map(m -> {
//                    User user = userRepository.findById(userId)
//                            .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
//                    return ChatMessageRes.builder()
//                            .id(m.getMessageId())
//                            .type(MessageType.CHAT)
//                            .roomId(m.getChatRoomId())
//                            .senderId(m.getUserId())
//                            .senderName(user.getName())
//                            .content(m.getContent())
//                            .sendAt(m.getCreatedAt())
//                            .emoji(user.getUserProfile().getEmoji())
//                            .build();
//                })
//                .toList();
//
//        dtoList.forEach(dto -> chatRedisService.saveToRedis(ChatMessageCacheDto.fromResponse(dto)));
//
//        return dtoList;
//    }

public List<ChatMessageRes> getMessagesByChatRoom(
        Long chatRoomId,
        Long userId,
        LocalDateTime lastMessageTime,
        int size
) {

    // 채팅방 참여 여부 검증
    if (!joinChatRepository.existsByUserIdAndChatRoomIdAndStatusActive(userId, chatRoomId)) {
        throw new BusinessException(Code.JOINCHAT_NOT_FOUND);
    }

    if (lastMessageTime == null) {
        lastMessageTime = LocalDateTime.now();
    }

    // 1) Redis에서 lastMessageTime 기준으로 size 만큼 메시지 조회 (가변 리스트)
    List<ChatMessageRes> cachedMessages = new ArrayList<>(
            chatRedisService.getMessages(chatRoomId, lastMessageTime, size)
    );

    // Redis에서 가져온 메시지에 senderName, emoji 정보 설정
    cachedMessages.forEach(msg -> {
        User user = userRepository.findById(msg.getSenderId())
                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
        msg.setSenderName(user.getUserProfile().getNickname());
        msg.setEmoji(user.getUserProfile().getEmoji());
    });

    int fetched = cachedMessages.size();

    // 2) Redis 데이터가 부족하면 DB에서 나머지 메시지 조회
    if (fetched < size) {
        int remaining = size - fetched;
        LocalDateTime dbStartTime = cachedMessages.isEmpty()
                ? lastMessageTime
                : cachedMessages.get(fetched - 1).getSendAt();

        List<ChatMessageRes> dbDtos = chatMongoService.getMessages(chatRoomId, dbStartTime, remaining)
                .stream()
                .map(m -> {
                    User user = userRepository.findById(m.getUserId())
                            .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
                    return ChatMessageRes.builder()
                            .id(m.getMessageId())
                            .type(m.getType())
                            .roomId(m.getChatRoomId())
                            .senderId(m.getUserId())
                            .senderName(user.getName())
                            .content(m.getContent())
                            .sendAt(m.getCreatedAt())
                            .emoji(user.getUserProfile().getEmoji())
                            .build();
                })
                .toList();

        // DB에서 가져온 메시지는 Redis에 캐싱
        dbDtos.forEach(dto -> chatRedisService.saveToRedis(chatRoomId, ChatMessageCacheDto.fromResponse(dto)));

        cachedMessages.addAll(dbDtos);
    }

    // 최신순 정렬 후 반환
    cachedMessages.sort(Comparator.comparing(ChatMessageRes::getSendAt).reversed());
    return cachedMessages;
}



}