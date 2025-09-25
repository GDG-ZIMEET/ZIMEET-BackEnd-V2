package com.gdg.z_meet.domain.chat.dto;

import com.gdg.z_meet.domain.chat.entity.Message;
import com.gdg.z_meet.domain.chat.entity.status.MessageType;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.io.Serializable;

@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class ChatMessageReq implements Serializable {
    private MessageType type;
    private Long roomId;
    private Long senderId;
    private String content;


//    public static ChatMessageReq fromEntity(Message message) {
//        return ChatMessageReq.builder()
//                .type(MessageType.CHAT)  // Mongo에는 type 없음 → 기본값 CHAT
//                .roomId(Long.parseLong(message.getChatRoomId()))
//                .senderId(Long.parseLong(message.getUserId()))
//                .content(message.getContent())
//                .build();
//    }
}
