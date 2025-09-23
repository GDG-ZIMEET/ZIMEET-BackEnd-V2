package com.gdg.z_meet.domain.chat.service;

import com.gdg.z_meet.domain.chat.dto.ChatMessageCacheDto;
import com.gdg.z_meet.domain.chat.dto.ChatMessageReq;
import com.gdg.z_meet.domain.chat.dto.ChatMessageRes;
import com.gdg.z_meet.domain.chat.entity.Message;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

import static com.gdg.z_meet.domain.chat.entity.status.MessageType.*;

@Service
@RequiredArgsConstructor
public class ChatMessageHandlerService {

    private final ChatRedisService chatRedisService;
    private final ChatMongoService chatMongoService;
    private final ChatNotificationService chatNotificationService;
    private final UserRepository userRepository;

    /**
     * 메시지 처리 엔트리포인트
     */
    public void sendMessage(ChatMessageReq messageReq) {
        switch (messageReq.getType()) {
            case ENTER -> sendEnter(messageReq);
            case EXIT -> sendExit(messageReq);
            case TALK -> sendTalk(messageReq);
            default -> sendTalk(messageReq);
        }
    }

    /**
     * 채팅 메시지 (Write-Through)
     */
    private void sendTalk(ChatMessageReq messageDto) {
        // 1) Redis Publish + 캐싱
        chatRedisService.publishAndCache(messageDto);

        // 2) MongoDB 저장
        chatMongoService.saveToMongo(messageDto);
    }

    /**
     * 입장 메시지
     */
    private void sendEnter(ChatMessageReq messageDto) {
        User user = userRepository.findById(messageDto.getSenderId())
                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));

        //setType

        chatRedisService.publishAndCache(messageDto);
        chatNotificationService.notifyRoomOpen(user, messageDto.getRoomId());

        // 👉 필요하다면 입장 기록도 Mongo에 저장
        // chatMongoService.saveToMongo(messageDto, messageDto.getRoomId());
    }

    /**
     * 퇴장 메시지
     */
    private void sendExit(ChatMessageReq messageDto) {
        User user = userRepository.findById(messageDto.getSenderId())
                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));

        //setType

        chatRedisService.publishAndCache(messageDto);

        // 👉 필요하다면 퇴장 기록도 Mongo에 저장
        // chatMongoService.saveToMongo(messageDto, messageDto.getRoomId());
    }

    /**
     * 메시지 조회 (Look-Aside)
     */
    public List<ChatMessageRes> getMessages(Long roomId, int size) {
        // 1) Redis 캐시 조회
        List<ChatMessageRes> cachedMessages = chatRedisService.getMessages(roomId, size);
        if (!cachedMessages.isEmpty()) {
            return cachedMessages;
        }

        // 2) Redis에 없으면 Mongo 조회
        List<Message> mongoMessages = chatMongoService.getMessagesBefore(roomId, LocalDateTime.now(), size);

        // 3) Mongo 결과 Redis 캐싱
        mongoMessages.forEach(m -> {
            ChatMessageRes dto = ChatMessageRes.fromEntity(m);

            ChatMessageCacheDto cacheDto = ChatMessageCacheDto.fromResponse(dto);

            chatRedisService.saveToRedis(cacheDto);
        });

        return mongoMessages.stream().map(ChatMessageRes::fromEntity).toList();
    }

//    public void handleMessage(ChatMessageDto messageDto) {
//        switch (messageDto.getType()) {
//            case ENTER:
//                handleEnter(messageDto);
//                break;
//            case EXIT:
//                handleExit(messageDto);
//                break;
//            case TALK:
//            default:
//                chatRedisService.publishMessage(messageDto);
//        }
//    }
//
//    private void handleEnter(ChatMessageDto messageDto) {
//        User user = userRepository.findById(messageDto.getSenderId())
//                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
//
//        //messageDto.setContent(user.getUserProfile().getNickname() + " 님이 입장하셨습니다.");
//        chatRedisService.publishMessage(messageDto);
//        chatNotificationService.notifyRoomOpen(user, messageDto.getRoomId());
//    }
//
//    private void handleExit(ChatMessageDto messageDto) {
//        User user = userRepository.findById(messageDto.getSenderId())
//                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
//
//        //messageDto.setContent(user.getUserProfile().getNickname() + " 님이 채팅방을 나갔습니다.");
//        chatRedisService.publishMessage(messageDto);
//    }


}

