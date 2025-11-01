package com.gdg.z_meet.domain.fcm.unit.service.chat;

import com.gdg.z_meet.domain.chat.dto.ChatMessageRes;
import com.gdg.z_meet.domain.chat.entity.ChatRoom;
import com.gdg.z_meet.domain.chat.entity.JoinChat;
import com.gdg.z_meet.domain.chat.entity.TeamChatRoom;
import com.gdg.z_meet.domain.chat.entity.status.ChatType;
import com.gdg.z_meet.domain.chat.entity.status.MessageType;
import com.gdg.z_meet.domain.chat.repository.ChatRoomRepository;
import com.gdg.z_meet.domain.chat.repository.JoinChatRepository;
import com.gdg.z_meet.domain.chat.repository.TeamChatRoomRepository;
import com.gdg.z_meet.domain.fcm.service.chat.FcmChatMessageServiceImpl;
import com.gdg.z_meet.domain.fcm.service.producer.FcmMessageProducer;
import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;
import java.util.Optional;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmChatMessageServiceImpl 단위 테스트")
class FcmChatMessageServiceImplTest {

    @Mock
    private FcmMessageProducer fcmMessageProducer;

    @Mock
    private ChatRoomRepository chatRoomRepository;

    @Mock
    private TeamChatRoomRepository teamChatRoomRepository;

    @Mock
    private JoinChatRepository joinChatRepository;

    @InjectMocks
    private FcmChatMessageServiceImpl fcmChatMessageService;

    private User sender;
    private User recipient;
    private ChatRoom chatRoom;

    @BeforeEach
    void setUp() {
        sender = User.builder()
                .id(1L)
                .name("발신자")
                .build();

        recipient = User.builder()
                .id(2L)
                .name("수신자")
                .userProfile(UserProfile.builder()
                        .nickname("수신자닉네임")
                        .build())
                .build();

        chatRoom = ChatRoom.builder()
                .id(1L)
                .chatType(ChatType.USER)
                .build();
    }

    @Test
    @DisplayName("USER 타입 채팅방 메시지 알림")
    void USER타입_채팅방_메시지알림() {
        ChatMessageRes messageRes = ChatMessageRes.builder()
                .roomId(1L)
                .senderId(1L)
                .content("안녕하세요")
                .type(MessageType.TALK)
                .build();

        JoinChat joinChat1 = JoinChat.builder().user(sender).build();
        JoinChat joinChat2 = JoinChat.builder().user(recipient).build();

        when(chatRoomRepository.findById(1L)).thenReturn(Optional.of(chatRoom));
        when(joinChatRepository.findByChatRoomId(1L)).thenReturn(List.of(joinChat1, joinChat2));

        fcmChatMessageService.messagingChat(messageRes);

        verify(fcmMessageProducer).sendSingleMessage(
                eq(2L),
                eq("수신자닉네임 (님)이 메시지를 보냈어요 💬"),
                eq("안녕하세요")
        );
    }

    @Test
    @DisplayName("TEAM 타입 채팅방 메시지 알림")
    void TEAM타입_채팅방_메시지알림() {
        ChatRoom teamChatRoom = ChatRoom.builder()
                .id(2L)
                .chatType(ChatType.TEAM)
                .build();

        Team team = Team.builder()
                .id(1L)
                .name("팀이름")
                .build();

        ChatMessageRes messageRes = ChatMessageRes.builder()
                .roomId(2L)
                .senderId(1L)
                .content("팀 메시지")
                .type(MessageType.TALK)
                .build();

        JoinChat joinChat1 = JoinChat.builder().user(sender).build();
        JoinChat joinChat2 = JoinChat.builder().user(recipient).build();

        when(chatRoomRepository.findById(2L)).thenReturn(Optional.of(teamChatRoom));
        when(joinChatRepository.findByChatRoomId(2L)).thenReturn(List.of(joinChat1, joinChat2));
        when(teamChatRoomRepository.findOtherTeamInChatRoom(2L, 1L)).thenReturn(Optional.of(team));

        fcmChatMessageService.messagingChat(messageRes);

        verify(fcmMessageProducer).sendSingleMessage(
                eq(2L),
                eq("팀이름 팀과의 채팅방에 메시지가 도착했어요 💬"),
                eq("팀 메시지")
        );
    }

    @Test
    @DisplayName("RANDOM 타입 채팅방 메시지 알림")
    void RANDOM타입_채팅방_메시지알림() {
        ChatRoom randomChatRoom = ChatRoom.builder()
                .id(3L)
                .chatType(ChatType.RANDOM)
                .build();

        TeamChatRoom teamChatRoom = TeamChatRoom.builder()
                .id(1L)
                .chatRoom(randomChatRoom)
                .name("랜덤채팅방")
                .build();

        ChatMessageRes messageRes = ChatMessageRes.builder()
                .roomId(3L)
                .senderId(1L)
                .content("랜덤 메시지")
                .type(MessageType.TALK)
                .build();

        JoinChat joinChat1 = JoinChat.builder().user(sender).build();
        JoinChat joinChat2 = JoinChat.builder().user(recipient).build();

        when(chatRoomRepository.findById(3L)).thenReturn(Optional.of(randomChatRoom));
        when(joinChatRepository.findByChatRoomId(3L)).thenReturn(List.of(joinChat1, joinChat2));
        when(teamChatRoomRepository.findFirstByChatRoomId(3L)).thenReturn(Optional.of(teamChatRoom));

        fcmChatMessageService.messagingChat(messageRes);

        verify(fcmMessageProducer).sendSingleMessage(
                eq(2L),
                eq("[랜덤채팅방] 채팅방에 메시지가 도착했어요 💬"),
                eq("랜덤 메시지")
        );
    }

    @Test
    @DisplayName("채팅방 없음 - 예외 발생")
    void 채팅방없음_예외발생() {
        ChatMessageRes messageRes = ChatMessageRes.builder()
                .roomId(999L)
                .senderId(1L)
                .content("메시지")
                .build();

        when(chatRoomRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fcmChatMessageService.messagingChat(messageRes));

        assertEquals(Code.CHATROOM_NOT_FOUND, exception.getCode());
        verify(fcmMessageProducer, never()).sendSingleMessage(any(), any(), any());
    }

    @Test
    @DisplayName("수신자 없음 - 알림 전송 안함")
    void 수신자없음_알림전송안함() {
        ChatMessageRes messageRes = ChatMessageRes.builder()
                .roomId(1L)
                .senderId(1L)
                .content("메시지")
                .build();

        JoinChat joinChat1 = JoinChat.builder().user(sender).build();

        when(chatRoomRepository.findById(1L)).thenReturn(Optional.of(chatRoom));
        when(joinChatRepository.findByChatRoomId(1L)).thenReturn(List.of(joinChat1));

        fcmChatMessageService.messagingChat(messageRes);

        verify(fcmMessageProducer, never()).sendSingleMessage(any(), any(), any());
    }

    @Test
    @DisplayName("채팅방 열림 알림 - USER 타입")
    void 채팅방열림알림_USER타입() {
        JoinChat joinChat = JoinChat.builder().user(recipient).build();

        when(chatRoomRepository.findById(1L)).thenReturn(Optional.of(chatRoom));
        when(joinChatRepository.findByChatRoomId(1L)).thenReturn(List.of(joinChat));

        fcmChatMessageService.messagingOpenChatRoom(sender, 1L);

        verify(fcmMessageProducer).sendSingleMessage(
                eq(1L),
                eq("수신자닉네임 님과의 채팅방이 열렸어요! 🤗"),
                eq("두근두근💗 새로운 사람들과 인사부터 시작해보세요!")
        );
    }

    @Test
    @DisplayName("채팅방 열림 알림 - 채팅방 없음")
    void 채팅방열림알림_채팅방없음() {
        when(chatRoomRepository.findById(999L)).thenReturn(Optional.empty());

        BusinessException exception = assertThrows(BusinessException.class,
                () -> fcmChatMessageService.messagingOpenChatRoom(sender, 999L));

        assertEquals(Code.CHATROOM_NOT_FOUND, exception.getCode());
        verify(fcmMessageProducer, never()).sendSingleMessage(any(), any(), any());
    }
}

