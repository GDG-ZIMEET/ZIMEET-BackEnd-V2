//package com.gdg.z_meet.domain.chat.service;
//
//import com.fasterxml.jackson.databind.ObjectMapper;
//import com.fasterxml.jackson.datatype.jsr310.JavaTimeModule;
//import com.gdg.z_meet.domain.chat.dto.ChatMessageDto;
//import com.gdg.z_meet.domain.chat.entity.ChatRoom;
//import com.gdg.z_meet.domain.chat.entity.Message;
//import com.gdg.z_meet.domain.chat.entity.status.MessageType;
//import com.gdg.z_meet.domain.chat.redis.RedisPublisher;
//import com.gdg.z_meet.domain.chat.repository.ChatRoomRepository;
//import com.gdg.z_meet.domain.chat.repository.JoinChatRepository;
//import com.gdg.z_meet.domain.chat.repository.mongo.MongoMessageRepository;
//import com.gdg.z_meet.domain.fcm.service.chat.FcmChatMessageServiceImpl;
//import com.gdg.z_meet.domain.user.entity.User;
//import com.gdg.z_meet.domain.user.repository.UserRepository;
//import com.gdg.z_meet.global.exception.BusinessException;
//import com.gdg.z_meet.global.response.Code;
//import com.mongodb.DuplicateKeyException;
//import jakarta.transaction.Transactional;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.data.domain.PageRequest;
//import org.springframework.data.domain.Pageable;
//import org.springframework.data.domain.Sort;
//import org.springframework.data.redis.core.RedisTemplate;
//import org.springframework.messaging.simp.SimpMessagingTemplate;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Service;
//
//import java.time.Instant;
//import java.time.LocalDateTime;
//import java.time.ZoneId;
//import java.util.*;
//import java.util.stream.Collectors;
//
///**
// * @Deprecated Redis pub/sub을 활용한 통합 채팅 메시지 서비스
// * 기존의 ChatService, MessageCommandService, MessageQueryService의 기능을 통합
// */
//@Deprecated
//@Slf4j
//@Service
//@RequiredArgsConstructor
//public class ChatMessageService {
//
//    // Redis 관련
//    private final RedisTemplate<String, Object> redisTemplate;
//    private final RedisPublisher redisPublisher;
//
//    // Repository
//    private final ChatRoomRepository chatRoomRepository;
//    private final MongoMessageRepository mongoMessageRepository;
//    private final JoinChatRepository joinChatRepository;
//    private final UserRepository userRepository;
//
//    // 기타 서비스
//    private final SimpMessagingTemplate messagingTemplate;
//    private final FcmChatMessageServiceImpl fcmChatMessageServiceImpl;
//    private final ObjectMapper objectMapper = new ObjectMapper().registerModule(new JavaTimeModule());
//
//    // Redis 키 상수
//    private static final String CHAT_ROOM_MESSAGES_KEY = "chatroom:%s:messages";
//    private static final String CHAT_ROOM_LATEST_MESSAGE_KEY = "chatroom:%s:latestMessage";
//    private static final String CHAT_ROOM_LATEST_MESSAGE_TIME_KEY = "chatroom:%s:latestMessageTime";
//    private static final int MAX_REDIS_MESSAGES = 300; // 최신 300개 Redis에 유지
//
//    /**
//     * 메시지 처리 메인 메서드 - Redis pub/sub을 통해 메시지 발행
//     */
//    public void sendMessage(Long roomId, ChatMessageDto chatMessageDto) {
//        try {
//            // Redis pub/sub을 통해 메시지 발행
//            redisPublisher.publishToRoom(roomId, chatMessageDto);
//            log.info("메시지 발행 완료 - roomId: {}, messageId: {}", chatMessageDto.getRoomId(), chatMessageDto.getId());
//        } catch (Exception e) {
//            log.error("메시지 발행 실패 - roomId: {}, messageId: {}", chatMessageDto.getRoomId(), chatMessageDto.getId(), e);
//            throw new BusinessException(Code.INTERNAL_SERVER_ERROR);
//        }
//
//        Message message = mongoMessageRepository.save(
//                Message.builder()
//                        .messageId(chatMessageDto.getId())
//                        .chatRoomId(roomId.toString())
//                        .userId(chatMessageDto.getSenderId().toString())
//                        .content(chatMessageDto.getContent())
//                        .createdAt(chatMessageDto.getSendAt())
//                        .updatedAt(LocalDateTime.now())
//                        .build()
//        );
//        log.info("MongoDB에 메시지 저장 완료 - messageId: {}", message.getMessageId());
//
//        notifyBackgroundUser(chatMessageDto);
//    }
//
//    /**
//     * Redis pub/sub을 통해 수신된 메시지 처리 (실제 stomp 전송 로직)
//     * RedisSubscriber에서 호출되는 메서드
//     */
//    public void handleReceivedMessage(ChatMessageDto chatMessageDto) {
//        try {
//            // 1. 메시지 저장 (Redis + MongoDB)
//            saveMessage(chatMessageDto);
//
//            // 2. 메시지 전송
////            broadcastMessage(chatMessage);
//            messagingTemplate.convertAndSend("/topic/" + chatMessageDto.getRoomId(), chatMessageDto);
//
//            // 3. 백그라운드 사용자에게 FCM 알림
//            notifyBackgroundUser(chatMessageDto);
//
//            log.info("메시지 처리 완료 - roomId: {}, messageId: {}", chatMessageDto.getRoomId(), chatMessageDto.getId());
//        } catch (Exception e) {
//            log.error("메시지 처리 실패 - roomId: {}, messageId: {}", chatMessageDto.getRoomId(), chatMessageDto.getId(), e);
//        }
//    }
//
//    /**
//     * 입장 메시지 처리
//     */
//    public void handleEnterMessage(Long roomId, String content) {
//        Long inviteId = parseUserId(content);
//        User user = findUserById(inviteId);
//
//        ChatMessageDto message = ChatMessageDto.builder()
//                .id(UUID.randomUUID().toString())
//                .type(MessageType.ENTER)
//                .roomId(roomId)
//                .senderId(user.getId())
//                .senderName(user.getUserProfile().getNickname())
//                .content(user.getUserProfile().getNickname() + " 님이 입장하셨습니다.")
//                .sendAt(LocalDateTime.now())
//                .emoji(null)
//                .build();
//
//        // FCM 알림
//        fcmChatMessageServiceImpl.messagingOpenChatRoom(user, roomId);
//
//        // 메시지 처리
//        sendMessage(roomId, message);
//    }
//
//    /**
//     * 퇴장 메시지 처리
//     */
//    public void handleExitMessage(Long roomId, String studentNumber, String senderName) {
//        User user = findUserByStudentNumber(studentNumber);
//        Long senderId = user.getId();
//
//        ChatMessageDto message = ChatMessageDto.builder()
//                .id(UUID.randomUUID().toString())
//                .type(MessageType.EXIT)
//                .roomId(roomId)
//                .senderId(senderId)
//                .senderName(senderName)
//                .content(senderName + " 님이 채팅방을 나갔습니다.")
//                .sendAt(LocalDateTime.now())
//                .emoji(null)
//                .build();
//
//        sendMessage(roomId, message);
//    }
//
//    /**
//     * 메시지 저장 (Redis + MongoDB)
//     */
//    @Transactional
//    public void saveMessage(ChatMessageDto chatMessageDto) {
//        Long chatRoomId = chatMessageDto.getRoomId();
//        String chatRoomMessagesKey = String.format(CHAT_ROOM_MESSAGES_KEY, chatRoomId);
//
//        // Redis에 이미 동일 messageId의 메시지가 있는지 확인
//        List<Object> recentMessages = redisTemplate.opsForList().range(chatRoomMessagesKey, -MAX_REDIS_MESSAGES, -1);
//        boolean isDuplicate = recentMessages != null && recentMessages.stream().anyMatch(
//                obj -> ((ChatMessageDto)obj).getId().equals(chatMessageDto.getId())
//        );
//
//        if (!isDuplicate) {
//            // Redis에 메시지 저장
//            redisTemplate.opsForList().rightPush(chatRoomMessagesKey, chatMessageDto);
//
//            // 최신 메시지 및 활동 시간 업데이트
//            String latestMessageKey = String.format(CHAT_ROOM_LATEST_MESSAGE_KEY, chatRoomId);
//            redisTemplate.opsForValue().set(latestMessageKey, chatMessageDto.getContent());
//
//            String latestMessageTimeKey = String.format(CHAT_ROOM_LATEST_MESSAGE_TIME_KEY, chatRoomId);
//            LocalDateTime latestMessageTime = LocalDateTime.now();
//            redisTemplate.opsForValue().set(latestMessageTimeKey, latestMessageTime.toString());
//        }
//    }
//
//    /**
//     * WebSocket을 통한 실시간 메시지 전송(redissubsrciber에서 호출)
//     */
////    public void broadcastMessage(ChatMessage chatMessage) {
////        log.info("채팅방 내 메시지 전송 messageId={}, roomId={}", chatMessage.getId(), chatMessage.getRoomId());
////        messagingTemplate.convertAndSend("/topic/" + chatMessage.getRoomId(), chatMessage);
////    }
//
//    /**
//     * 백그라운드 사용자에게 FCM 알림
//     */
//    public void notifyBackgroundUser(ChatMessageDto chatMessageDto) {
//        try {
//            fcmChatMessageServiceImpl.messagingChat(chatMessageDto);
//        } catch (Exception e) {
//            log.warn("FCM 전송 실패 - chatRoomId={}, senderId={}", chatMessageDto.getRoomId(), chatMessageDto.getSenderId(), e);
//        }
//    }
//
//    /**
//     * 채팅방 메시지 조회 (Redis + MongoDB)
//     */
//    public List<ChatMessageDto> getMessagesByChatRoom(Long chatRoomId, Long userId, LocalDateTime lastMessageTime, int size) {
//        // 채팅방 참여 여부 확인
//        if (!joinChatRepository.existsByUserIdAndChatRoomIdAndStatusActive(userId, chatRoomId)) {
//            throw new BusinessException(Code.JOINCHAT_NOT_FOUND);
//        }
//
//        if (lastMessageTime == null) {
//            lastMessageTime = LocalDateTime.now();
//        }
//
//        // LocalDateTime → UTC Date 변환
//        Instant instant = lastMessageTime.atZone(ZoneId.systemDefault()).toInstant();
//        Date utcDate = Date.from(instant);
//
//        // Redis에서 메시지 조회
//        String redisKey = String.format(CHAT_ROOM_MESSAGES_KEY, chatRoomId);
//        List<Object> redisRaw = redisTemplate.opsForList().range(redisKey, 0, -1);
//
//        // Redis에서 lastMessageTime 이전의 메시지 필터링 및 최신순 정렬
//        LocalDateTime finalLastMessageTime = lastMessageTime;
//        List<ChatMessageDto> redisMessages = Optional.ofNullable(redisRaw).orElse(List.of()).stream()
//                .map(obj -> (ChatMessageDto) obj)
//                .filter(m -> m.getSendAt() != null && m.getSendAt().isBefore(finalLastMessageTime))
//                .sorted(Comparator.comparing(ChatMessageDto::getSendAt).reversed())
//                .limit(size)
//                .collect(Collectors.toList());
//
//        int fetched = redisMessages.size();
//
//        // Redis에서 부족한 개수만큼 MongoDB에서 추가 조회
//        if (fetched < size) {
//            int remaining = size - fetched;
//            Pageable pageable = PageRequest.of(0, remaining, Sort.by("createdAt").descending());
//
//            List<Message> dbMessages = mongoMessageRepository.findByChatRoomIdAndCreatedAtBefore(
//                    chatRoomId.toString(), utcDate, pageable
//            );
//
//            // Redis에 없는 메시지만 추가
//            Set<String> redisIds = redisMessages.stream()
//                    .map(ChatMessageDto::getId)
//                    .collect(Collectors.toSet());
//
//            List<ChatMessageDto> dbChatMessageDtos = dbMessages.stream()
//                    .filter(m -> m.getMessageId() != null && !redisIds.contains(m.getMessageId()))
//                    .map(m -> {
//                        User user = userRepository.findById(Long.parseLong(m.getUserId()))
//                                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
//                        return ChatMessageDto.builder()
//                                .id(m.getMessageId())
//                                .type(MessageType.CHAT)
//                                .roomId(Long.parseLong(m.getChatRoomId()))
//                                .senderId(Long.parseLong(m.getUserId()))
//                                .senderName(user.getName())
//                                .content(m.getContent())
//                                .sendAt(m.getCreatedAt())
//                                .emoji(user.getUserProfile().getEmoji())
//                                .build();
//                    })
//                    .collect(Collectors.toList());
//
//            redisMessages.addAll(dbChatMessageDtos);
//        }
//
//        return redisMessages.stream()
//                .sorted(Comparator.comparing(ChatMessageDto::getSendAt).reversed())
//                .collect(Collectors.toList());
//    }
////
////    /**
////     * Redis 메시지를 MongoDB로 배치 저장 (스케줄러)
////     */
////    @Scheduled(fixedRate = 60000) // 1분마다 실행
////    @Transactional
////    public void saveMessagesToDB() {
////        List<ChatRoom> chatRooms = chatRoomRepository.findAll();
////
////        for (ChatRoom chatRoom : chatRooms) {
////            Long chatRoomId = chatRoom.getId();
////            String chatRoomMessagesKey = String.format(CHAT_ROOM_MESSAGES_KEY, chatRoomId);
////
////            Long totalMessages = redisTemplate.opsForList().size(chatRoomMessagesKey);
////            if (totalMessages == null || totalMessages == 0) continue;
////
////            List<Object> messages = redisTemplate.opsForList().range(chatRoomMessagesKey, 0, -1);
////            if (messages == null || messages.isEmpty()) continue;
////
////            // Redis에서 가져온 메시지를 ChatMessage 객체로 변환
////            List<ChatMessageDto> chatMessageDtos = messages.stream()
////                    .map(obj -> objectMapper.convertValue(obj, ChatMessageDto.class))
////                    .filter(chatMessage -> chatMessage.getSenderId() != null)
////                    .toList();
////
////            // Redis에서 가져온 UUID 모두 수집
////            Set<String> incomingMessageIds = chatMessageDtos.stream()
////                    .map(ChatMessageDto::getId)
////                    .collect(Collectors.toSet());
////
////            // MongoDB에 이미 저장된 UUID 조회
////            Set<String> existingIds = mongoMessageRepository.findByMessageIdIn(incomingMessageIds)
////                    .stream()
////                    .map(Message::getMessageId)
////                    .collect(Collectors.toSet());
////
////            // 중복 메시지 제거
////            List<Message> messageList = chatMessageDtos.stream()
////                    .filter(msg -> !existingIds.contains(msg.getId()))
////                    .map(chatMessage -> {
////                        User user = userRepository.findById(chatMessage.getSenderId())
////                                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
////                        LocalDateTime now = LocalDateTime.now();
////
////                        return Message.builder()
////                                .messageId(chatMessage.getId())
////                                .chatRoomId(chatRoomId.toString())
////                                .userId(user.getId().toString())
////                                .content(chatMessage.getContent())
////                                .createdAt(chatMessage.getSendAt())
////                                .updatedAt(now)
////                                .build();
////                    })
////                    .toList();
////
////            try {
////                mongoMessageRepository.saveAll(messageList);
////                log.info("✅ MongoDB 저장 완료 - 저장된 메시지 수: {}", messageList.size());
////            } catch (DuplicateKeyException e) {
////                log.warn("MongoDB 중복 UUID 충돌 발생: {}", e.getMessage());
////            } catch (Exception e) {
////                log.error("Mongo 저장 실패", e);
////            }
////
////            // Redis에서 최신 n개의 메시지를 제외하고 모두 삭제
////            if (totalMessages > MAX_REDIS_MESSAGES) {
////                redisTemplate.opsForList().trim(chatRoomMessagesKey, -MAX_REDIS_MESSAGES, -1);
////            }
////        }
////    }
//
//    // 유틸리티 메서드들
//    private Long parseUserId(String content) {
//        try {
//            return Long.parseLong(content);
//        } catch (NumberFormatException e) {
//            throw new IllegalArgumentException("Invalid userId format: " + content);
//        }
//    }
//
//    private User findUserById(Long userId) {
//        return userRepository.findById(userId)
//                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
//    }
//
//    private User findUserByStudentNumber(String studentNumber) {
//        return userRepository.findByStudentNumber(studentNumber)
//                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
//    }
//}
//
//
