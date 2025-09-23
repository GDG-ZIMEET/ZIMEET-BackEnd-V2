//package com.gdg.z_meet.domain.chat.scheduler;
//
//import com.gdg.z_meet.domain.chat.dto.ChatMessageDto;
//import com.gdg.z_meet.domain.chat.service.ChatMongoService;
//import com.gdg.z_meet.domain.chat.service.ChatRedisService;
//import com.gdg.z_meet.domain.chat.repository.ChatRoomRepository;
//import com.gdg.z_meet.domain.chat.entity.ChatRoom;
//import lombok.RequiredArgsConstructor;
//import lombok.extern.slf4j.Slf4j;
//import org.springframework.scheduling.annotation.Scheduled;
//import org.springframework.stereotype.Component;
//
//import java.util.List;
//
//@Slf4j
//@Component
//@RequiredArgsConstructor
//public class ChatMessageScheduler {
//
//    private final ChatRoomRepository chatRoomRepository;
//    private final ChatRedisService chatRedisService;
//    private final ChatMongoService chatMongoService;
//
//    /**
//     * 1분마다 Redis에 쌓인 메시지를 MongoDB로 백업
//     */
//    @Scheduled(fixedRate = 60000) // 60초마다 실행
//    public void backupMessages() {
//        List<ChatRoom> chatRooms = chatRoomRepository.findAll();
//
//        for (ChatRoom chatRoom : chatRooms) {
//            Long roomId = chatRoom.getId();
//
//            // Redis에서 메시지 가져오기 (최신 300개 중에서 N개만 유지)
//            List<ChatMessageDto> messages = chatRedisService.getMessages(roomId, 300);
//            if (messages.isEmpty()) continue;
//
//            try {
//                // MongoDB에 저장
//                chatMongoService.saveBatchToMongo(messages, roomId);
//
//                log.info("✅ [채팅방 {}] MongoDB 백업 완료 - 저장된 메시지 수: {}", roomId, messages.size());
//            } catch (Exception e) {
//                log.error("❌ [채팅방 {}] MongoDB 백업 실패", roomId, e);
//            }
//        }
//    }
//}
//
