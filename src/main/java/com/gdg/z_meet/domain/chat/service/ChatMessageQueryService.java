//package com.gdg.z_meet.domain.chat.service;
//
//import com.gdg.z_meet.domain.chat.dto.ChatMessageDto;
//import com.gdg.z_meet.domain.chat.repository.JoinChatRepository;
//import com.gdg.z_meet.global.exception.BusinessException;
//import com.gdg.z_meet.global.response.Code;
//import lombok.RequiredArgsConstructor;
//import org.springframework.stereotype.Service;
//
//import java.time.LocalDateTime;
//import java.util.Comparator;
//import java.util.List;
//import java.util.Set;
//import java.util.stream.Collectors;
//
//@RequiredArgsConstructor
//@Service
//public class ChatMessageQueryService {
//
//    private final ChatRedisService chatRedisService;
//    private final ChatMongoService chatMongoService;
//    private final JoinChatRepository joinChatRepository;
//
//    public List<ChatMessageDto> getMessages(Long chatRoomId, Long userId, LocalDateTime lastMessageTime, int size) {
//        if (!joinChatRepository.existsByUserIdAndChatRoomIdAndStatusActive(userId, chatRoomId)) {
//            throw new BusinessException(Code.JOINCHAT_NOT_FOUND);
//        }
//
//        if (lastMessageTime == null) lastMessageTime = LocalDateTime.now();
//
//        // 1. Redis에서 먼저 가져오기
//        List<ChatMessageDto> redisMessages = chatRedisService.getMessagesBefore(chatRoomId, lastMessageTime, size);
//        int fetched = redisMessages.size();
//
//        // 2. 부족하면 MongoDB에서 가져오기
//        if (fetched < size) {
//            Set<String> redisIds = redisMessages.stream().map(ChatMessageDto::getId).collect(Collectors.toSet());
//            int remaining = size - fetched;
//            List<ChatMessageDto> dbMessages = chatMongoService.getMessagesBefore(chatRoomId, lastMessageTime, remaining, redisIds);
//            redisMessages.addAll(dbMessages);
//        }
//
//        // 3. 최신순으로 정렬
//        return redisMessages.stream()
//                .sorted(Comparator.comparing(ChatMessageDto::getSendAt).reversed())
//                .collect(Collectors.toList());
//    }
//}
