package com.gdg.z_meet.domain.chat.service;

import com.gdg.z_meet.domain.chat.dto.ChatMessageRes;
import com.gdg.z_meet.domain.fcm.service.chat.FcmChatMessageServiceImpl;
import com.gdg.z_meet.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Slf4j
@Service
@RequiredArgsConstructor
public class ChatNotificationService {

    private final FcmChatMessageServiceImpl fcmService;

    public void notifyRoomOpen(User user, Long roomId) {
        try {
            fcmService.messagingOpenChatRoom(user, roomId);
        } catch (Exception e) {
            log.warn("FCM 알림 실패 - roomId={}", roomId, e);
        }
    }

    public void notifyMessage(ChatMessageRes messageDto) {
        try {
            fcmService.messagingChat(messageDto);
        } catch (Exception e) {
            log.warn("FCM 메시지 알림 실패 - roomId={}, senderId={}", messageDto.getRoomId(), messageDto.getSenderId(), e);
        }
    }
}

