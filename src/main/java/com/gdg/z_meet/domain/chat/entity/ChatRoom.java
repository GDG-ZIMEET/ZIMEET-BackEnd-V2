package com.gdg.z_meet.domain.chat.entity;

import com.gdg.z_meet.domain.chat.entity.status.ChatType;
import com.gdg.z_meet.global.common.BaseEntity;
import jakarta.persistence.*;
import lombok.*;

@Entity
@Getter
@Builder
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor
public class ChatRoom extends BaseEntity {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    @Column(name = "chat_room_id", unique = true)
    private Long id;

    @Enumerated(EnumType.STRING)
    private ChatType chatType;

    @Column(unique = true)
    private Long randomChatId;
}
