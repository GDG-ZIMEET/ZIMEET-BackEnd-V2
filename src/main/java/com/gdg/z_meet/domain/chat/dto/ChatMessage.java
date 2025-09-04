package com.gdg.z_meet.domain.chat.dto;

import com.gdg.z_meet.domain.chat.entity.status.MessageType;
import lombok.*;

import java.io.Serializable;
import java.time.LocalDateTime;
import java.time.ZoneId;
import java.util.UUID;

@Getter
@AllArgsConstructor
@NoArgsConstructor
@Builder
public class ChatMessage implements Serializable {
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

}
