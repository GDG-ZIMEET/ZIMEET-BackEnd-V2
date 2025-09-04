package com.gdg.z_meet.domain.chat.service;

import com.gdg.z_meet.domain.chat.dto.ChatMessage;
import com.gdg.z_meet.domain.chat.entity.status.MessageType;
import com.gdg.z_meet.domain.fcm.service.custom.FcmChatMessageService;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
public class ChatService {
    private final MessageCommandService messageCommandService;
    private final UserRepository userRepository;
    private final FcmChatMessageService fcmChatMessageService;

    public void handleMessage(ChatMessage message) {
        messageCommandService.processMessage(message);
    }

    public void handleEnterMessage(Long roomId, String content) {
        Long inviteId = parseUserId(content);
        User user = findUserById(inviteId);
        ChatMessage message = ChatMessage.builder()
                .type(MessageType.ENTER)
                .roomId(roomId)
                .senderId(user.getId())
                .senderName(user.getUserProfile().getNickname())
                .content(user.getUserProfile().getNickname() + " 님이 입장하셨습니다.")
                .sendAt(LocalDateTime.now())
                .emoji(null) // 입장/퇴장 메시지는 이모지 필요 없음
                .build();

        fcmChatMessageService.messagingOpenChatRoom(user, roomId);

        handleMessage(message);
    }

    public void handleTalkMessage(ChatMessage message) {
        handleMessage(message);
    }

    public void handleExitMessage(Long roomId, Long senderId, String senderName) {
        ChatMessage message = ChatMessage.builder()
                .type(MessageType.EXIT)
                .roomId(roomId)
                .senderId(senderId)
                .senderName(senderName)
                .content(senderName + " 님이 채팅방을 나갔습니다.")
                .sendAt(LocalDateTime.now())
                .emoji(null) // 입장/퇴장 메시지는 이모지 필요 없음
                .build();
        handleMessage(message);
    }

    private Long parseUserId(String content) {
        try {
            return Long.parseLong(content);
        } catch (NumberFormatException e) {
            throw new IllegalArgumentException("Invalid userId format: " + content);
        }
    }

    private User findUserById(Long userId) {
        return userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
    }
}
