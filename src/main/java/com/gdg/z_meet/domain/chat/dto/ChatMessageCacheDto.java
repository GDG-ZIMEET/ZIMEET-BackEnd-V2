package com.gdg.z_meet.domain.chat.dto;

import com.gdg.z_meet.domain.chat.entity.status.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.util.UUID;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageCacheDto implements Serializable {

    private String id;
    private MessageType type;
    private Long roomId;
    private Long senderId;
    private String content;
    private LocalDateTime sendAt;

    // Request → Cache 변환
    public static ChatMessageCacheDto fromReq(ChatMessageReq request) {
        return new ChatMessageCacheDto(
                UUID.randomUUID().toString(),
                request.getType(),
                request.getRoomId(),
                request.getSenderId(),
                request.getContent(),
                LocalDateTime.now()
        );
    }

    // Response → Cache 변환
    public static ChatMessageCacheDto fromResponse(ChatMessageRes response) {
        return new ChatMessageCacheDto(
                response.getId(),
                response.getType(),
                response.getRoomId(),
                response.getSenderId(),
                response.getContent(),
                response.getSendAt()
        );
    }
}
