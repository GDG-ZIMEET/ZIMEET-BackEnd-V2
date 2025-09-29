package com.gdg.z_meet.domain.chat.dto;

import com.gdg.z_meet.domain.chat.entity.Message;
import com.gdg.z_meet.domain.chat.entity.status.MessageType;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageRes implements Serializable {
    @Builder.Default
    private String id = UUID.randomUUID().toString();
    private MessageType type;
    private Long roomId;
    private Long senderId;
    private String senderName;
    private String content;
    @Builder.Default
    private LocalDateTime sendAt = LocalDateTime.now(ZoneId.of("Asia/Seoul"));

    private String emoji;

    public static ChatMessageRes fromEntity(Message message) {
        return ChatMessageRes.builder()
                .id(message.getMessageId())
                .type(MessageType.CHAT)  // Mongo에는 type 없음 → 기본값 CHAT
                .roomId(message.getChatRoomId())
                .senderId(message.getUserId())
                .content(message.getContent())
                .sendAt(message.getCreatedAt())
                // senderName, emoji는 DB에 없으므로 null → Service 계층에서 UserRepository로 보강 가능
                .build();
    }

    public void setSenderName(String name) {
        this.senderName = name;
    }

    public void setEmoji(String emoji) {
        this.emoji = emoji;
    }
}
