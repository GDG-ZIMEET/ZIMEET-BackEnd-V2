package com.gdg.z_meet.domain.chat.entity;

import lombok.*;

import org.bson.types.ObjectId;
import org.springframework.data.mongodb.core.index.Indexed;
import org.springframework.data.mongodb.core.mapping.Document;
import org.springframework.data.annotation.Id;
import org.springframework.data.mongodb.core.mapping.Field;

import java.time.LocalDateTime;

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

    private String content;
    private Boolean isRead;

    private String userId;
    private String chatRoomId;

    @Field("createdAt")
    private LocalDateTime createdAt;

    @Field("updatedAt")
    private LocalDateTime updatedAt;

}
