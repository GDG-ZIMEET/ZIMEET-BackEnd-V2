package com.gdg.z_meet.domain.chat.service;

import com.gdg.z_meet.domain.chat.dto.ChatMessageReq;
import com.gdg.z_meet.domain.chat.entity.Message;
import com.gdg.z_meet.domain.chat.repository.mongo.MessageRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.Date;
import java.util.List;
import java.util.UUID;

@Service
@RequiredArgsConstructor
public class ChatMongoService {

    private final MessageRepository messageRepository;
    private final UserRepository userRepository;

    // Write-Through: MongoDB 저장
    @Transactional
    public void saveToMongo(Long roomId, ChatMessageReq req) {
        User user = userRepository.findById(req.getSenderId())
                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));

        Message message = Message.builder()
                .messageId(UUID.randomUUID().toString())
                .type(req.getType())
                .chatRoomId(roomId)
                .userId(user.getId())
                .content(req.getContent())
                .createdAt(LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();

        messageRepository.save(message);
    }

    // Look-Aside: DB 조회 (Redis에 캐싱은 외부에서 수행)
    public List<Message> getMessages(Long chatRoomId, LocalDateTime lastMessageTime, int size) {
        if (lastMessageTime == null) {
            lastMessageTime = LocalDateTime.now();
        }

        Instant instant = lastMessageTime.atZone(ZoneId.systemDefault()).toInstant();
        Date utcDate = Date.from(instant);

        Pageable pageable = PageRequest.of(0, size, Sort.by("createdAt").descending());

        // createdAt < lastMessageTime (엄격하게 이전 메시지만 조회)
        return messageRepository.findByChatRoomIdAndCreatedAtBefore(
                chatRoomId, utcDate, pageable
        );
    }


//    // Look-Aside: DB 조회 (Redis에 캐싱은 외부에서 수행)
//    public List<Message> getMessagesBefore(Long roomId, LocalDateTime before, int limit) {
//
//        Instant instant = before.atZone(ZoneId.systemDefault()).toInstant(); // 시스템 시간대 → UTC
//        Date utcDate = Date.from(instant); // MongoDB 비교용
//
//        return messageRepository.findByChatRoomIdAndCreatedAtBefore(
//                roomId.toString(), utcDate, org.springframework.data.domain.PageRequest.of(0, limit)
//        );
//    }

//    @Transactional
//    public void saveBatchToMongo(List<ChatMessageDto> messageDtos, Long roomId) {
//        Set<String> ids = mongoMessageRepository.findByMessageIdIn(
//                        messageDtos.stream().map(ChatMessageDto::getId).collect(Collectors.toSet()))
//                .stream().map(m -> m.getMessageId()).collect(Collectors.toSet());
//
//        List<Message> messagesToSave = messageDtos.stream()
//                .filter(m -> !ids.contains(m.getId()))
//                .map(dto -> {
//                    User user = userRepository.findById(dto.getSenderId())
//                            .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
//                    return Message.builder()
//                            .messageId(dto.getId())
//                            .chatRoomId(roomId.toString())
//                            .userId(user.getId().toString())
//                            .content(dto.getContent())
//                            .createdAt(dto.getSendAt())
//                            .updatedAt(LocalDateTime.now())
//                            .build();
//                }).toList();
//
//        mongoMessageRepository.saveAll(messagesToSave);
//    }

//    public List<ChatMessageDto> getMessagesBefore(Long roomId, LocalDateTime before, int limit, Set<String> excludeIds) {
//        Instant instant = before.atZone(ZoneId.systemDefault()).toInstant();
//        Date utcDate = Date.from(instant);
//
//        Pageable pageable = PageRequest.of(0, limit, Sort.by("createdAt").descending());
//        List<Message> dbMessages = mongoMessageRepository.findByChatRoomIdAndCreatedAtBefore(roomId.toString(), utcDate, pageable);
//
//        return dbMessages.stream()
//                .filter(m -> m.getMessageId() != null && !excludeIds.contains(m.getMessageId()))
//                .map(m -> {
//                    User user = userRepository.findById(Long.parseLong(m.getUserId()))
//                            .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
//                    return ChatMessageDto.builder()
//                            .id(m.getMessageId())
//                            .type(MessageType.CHAT)
//                            .roomId(Long.parseLong(m.getChatRoomId()))
//                            .senderId(Long.parseLong(m.getUserId()))
//                            .senderName(user.getName())
//                            .content(m.getContent())
//                            .sendAt(m.getCreatedAt())
//                            .emoji(user.getUserProfile().getEmoji())
//                            .build();
//                })
//                .collect(Collectors.toList());
//    }

}

