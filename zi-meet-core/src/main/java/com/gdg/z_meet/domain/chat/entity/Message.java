package com.gdg.z_meet.domain.chat.entity;

import com.gdg.z_meet.domain.chat.dto.ChatMessageRes;
import com.gdg.z_meet.domain.chat.entity.status.MessageType;
import lombok.*;

import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;
import java.util.UUID;

@Document(collection = "messages")
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class Message {

    @Id
    private String id;     // MongoDB 기존 ObjectId

    @Field("messageId")
    @Indexed(unique = true)
    private String messageId;     // UUID

    private MessageType type;

    private String content;
    private Boolean isRead;

    private Long userId;
    private Long chatRoomId;

    @Field("createdAt")
    private LocalDateTime createdAt;

    @Field("updatedAt")
    private LocalDateTime updatedAt;

    public static Message of(ChatMessageRes dto) {
        return Message.builder()
                .messageId(dto.getId() != null ? dto.getId() : UUID.randomUUID().toString())
                .type(dto.getType())
                .content(dto.getContent())
                .isRead(false)
                .userId(dto.getSenderId())
                .chatRoomId(dto.getRoomId())
                .createdAt(dto.getSendAt() != null ? dto.getSendAt() : LocalDateTime.now())
                .updatedAt(LocalDateTime.now())
                .build();
    }
}
