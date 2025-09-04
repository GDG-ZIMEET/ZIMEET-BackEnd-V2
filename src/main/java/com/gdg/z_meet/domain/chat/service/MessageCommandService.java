package com.gdg.z_meet.domain.chat.service;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
import com.gdg.z_meet.domain.chat.dto.ChatMessage;
import com.gdg.z_meet.domain.chat.entity.ChatRoom;
import com.gdg.z_meet.domain.chat.entity.Message;
import com.gdg.z_meet.domain.chat.repository.ChatRoomRepository;
import com.gdg.z_meet.domain.chat.repository.mongo.MongoMessageRepository;
import com.gdg.z_meet.domain.fcm.service.custom.FcmChatMessageService;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import com.mongodb.DuplicateKeyException;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class MessageCommandService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChatRoomRepository chatRoomRepository;
    private final SimpMessagingTemplate messagingTemplate;
    private final MongoMessageRepository mongoMessageRepository;
    private final UserRepository userRepository;
    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());

    private static final String CHAT_ROOM_MESSAGES_KEY = "chatroom:%s:messages";
    private static final String CHAT_ROOM_LATEST_MESSAGE_KEY = "chatroom:%s:latestMessage";
    private static final String CHAT_ROOM_LATEST_MESSAGE_TIME_KEY = "chatroom:%s:latestMessageTime";
    private static final int MAX_REDIS_MESSAGES = 300; // 최신 300개 Redis에 유지

    private final FcmChatMessageService fcmChatMessageService;

    @Transactional
    public void processMessage(ChatMessage chatMessage) {
        saveMessage(chatMessage);
        broadcastMessage(chatMessage);
        notifyBackgroundUser(chatMessage);
    }

    public void notifyBackgroundUser(ChatMessage chatMessage) {
        try {
            fcmChatMessageService.messagingChat(chatMessage);
        } catch (Exception e) {
            log.warn("FCM 전송 실패 - chatRoomId={}, senderId={}", chatMessage.getRoomId(), chatMessage.getSenderId(), e);
        }
    }

    @Transactional
    public void saveMessage(ChatMessage chatMessage) {
        Long chatRoomId = chatMessage.getRoomId();

        // Redis에 메시지 저장
        String chatRoomMessagesKey = String.format(CHAT_ROOM_MESSAGES_KEY, chatRoomId);

        // Redis에 이미 동일 messageId의 메시지가 있는지 확인
        List<Object> recentMessages = redisTemplate.opsForList().range(chatRoomMessagesKey, -MAX_REDIS_MESSAGES, -1);
        boolean isDuplicate = recentMessages != null && recentMessages.stream().anyMatch(
                obj -> ((ChatMessage)obj).getId().equals(chatMessage.getId())
        );

        if (!isDuplicate) {
            redisTemplate.opsForList().rightPush(chatRoomMessagesKey, chatMessage);

            // 최신 메시지 및 활동 시간 업데이트
            String latestMessageKey = String.format(CHAT_ROOM_LATEST_MESSAGE_KEY, chatRoomId);
            redisTemplate.opsForValue().set(latestMessageKey, chatMessage.getContent());

            String latestMessageTimeKey = String.format(CHAT_ROOM_LATEST_MESSAGE_TIME_KEY, chatRoomId);
            LocalDateTime latestMessageTime = LocalDateTime.now();
            redisTemplate.opsForValue().set(latestMessageTimeKey, latestMessageTime.toString()); // 시간도 저장
        }
    }

    public void broadcastMessage(ChatMessage chatMessage) {
        // 채팅방 참여자들에게 메시지 전송
        log.info("채팅방 내 메시지 전송 messageId={}, roomId={}", chatMessage.getId(), chatMessage.getRoomId());
        messagingTemplate.convertAndSend("/topic/" + chatMessage.getRoomId(), chatMessage);
    }

    @Scheduled(fixedRate = 60000) // 1분마다 실행
    @Transactional
    public void saveMessagesToDB() {

        List<ChatRoom> chatRooms = chatRoomRepository.findAll();  // MySQL에서 chatRoom 조회

        for (ChatRoom chatRoom : chatRooms) {
            Long chatRoomId = chatRoom.getId();
            String chatRoomMessagesKey = String.format(CHAT_ROOM_MESSAGES_KEY, chatRoomId);

            Long totalMessages = redisTemplate.opsForList().size(chatRoomMessagesKey);
            if (totalMessages == null || totalMessages == 0) continue; // 저장할 메시지가 없음

            List<Object> messages = redisTemplate.opsForList().range(chatRoomMessagesKey, 0, -1);
            if (messages == null || messages.isEmpty()) continue;

            List<ChatMessage> chatMessages = messages.stream()
                    .map(obj -> objectMapper.convertValue(obj, ChatMessage.class))
                    .filter(chatMessage -> chatMessage.getSenderId() != null)
                    .toList();

            // Redis에서 가져온 UUID 모두 수집
            Set<String> incomingMessageIds = chatMessages.stream()
                    .map(ChatMessage::getId)
                    .collect(Collectors.toSet());

            // MongoDB에 이미 저장된 UUID 조회
            Set<String> existingIds = mongoMessageRepository.findByMessageIdIn(incomingMessageIds)
                    .stream()
                    .map(Message::getMessageId)
                    .collect(Collectors.toSet());


            List<Message> messageList = chatMessages.stream()
                    .filter(msg -> !existingIds.contains(msg.getId()))
                    .map(chatMessage -> {
                        User user = userRepository.findById(chatMessage.getSenderId())
                                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
                        LocalDateTime now = LocalDateTime.now();

                        return Message.builder()
                                .messageId(chatMessage.getId())
                                .chatRoomId(chatRoomId.toString())
                                .userId(user.getId().toString())
                                .content(chatMessage.getContent())
                                .createdAt(chatMessage.getSendAt())
                                .updatedAt(now)
                                .build();
                    })
                    .toList();

//            log.info("Redis 메시지 수: {}", chatMessages.size());
//            log.info("MongoDB 저장할 메시지 수: {}", messageList.size());

            try {
                mongoMessageRepository.saveAll(messageList);
                log.info("✅ MongoDB 저장 완료 - 저장된 메시지 수: {}", messageList.size());
            }  catch (DuplicateKeyException e) {
                log.warn("MongoDB 중복 UUID 충돌 발생: {}", e.getMessage());
            }  catch (Exception e) {
                log.error("Mongo 저장 실패", e);
            }
             //레디스에서 최신 n개의 메시지를 제외하고 모두 저장
            if (totalMessages > MAX_REDIS_MESSAGES) {
                redisTemplate.opsForList().trim(chatRoomMessagesKey, -MAX_REDIS_MESSAGES, -1);
            }
        }
    }
}
