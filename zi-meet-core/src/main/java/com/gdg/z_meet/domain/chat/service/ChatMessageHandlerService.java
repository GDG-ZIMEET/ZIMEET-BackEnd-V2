package com.gdg.z_meet.domain.chat.service;

import com.gdg.z_meet.domain.chat.dto.ChatMessageReq;
import com.gdg.z_meet.domain.chat.dto.ChatMessageRes;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;


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
    public void sendMessage(Long roomId, Long userId, ChatMessageReq messageReq) {
        switch (messageReq.getType()) {
            case ENTER -> sendEnter(roomId, userId, messageReq);
            case EXIT -> sendExit(roomId, userId, messageReq);
            case TALK -> sendTalk(roomId, userId, messageReq);
            default -> sendTalk(roomId, userId, messageReq);
        }
    }

    /**
     * 채팅 메시지 (Write-Through)
     */
    private void sendTalk(Long roomId, Long userId, ChatMessageReq messageDto) {
        // 1) Redis Publish + 캐싱
        chatRedisService.publishAndCache(roomId, userId, messageDto);

        // 2) MongoDB 저장
        chatMongoService.saveToMongo(roomId, userId, messageDto);

        // 3) 알림 전송 (RabbitMQ 적재)
        ChatMessageRes res = ChatMessageRes.builder()
                .type(messageDto.getType())
                .roomId(roomId)
                .senderId(userId)
                .content(messageDto.getContent())
                .build();
        chatNotificationService.notifyMessage(res);
    }

    /**
     * 입장 메시지
     */
    private void sendEnter(Long roomId, Long userId, ChatMessageReq messageDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));

        // setType

        chatRedisService.publishAndCache(roomId, userId, messageDto);
        chatNotificationService.notifyRoomOpen(user, roomId);
    }

    /**
     * 퇴장 메시지
     */
    private void sendExit(Long roomId, Long userId, ChatMessageReq messageDto) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));

        // setType

        chatRedisService.publishAndCache(roomId, userId, messageDto);
    }

}
