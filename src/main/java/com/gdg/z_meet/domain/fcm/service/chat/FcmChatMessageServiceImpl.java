
package com.gdg.z_meet.domain.fcm.service.chat;

import com.gdg.z_meet.domain.chat.dto.ChatMessageRes;
import com.gdg.z_meet.domain.chat.entity.ChatRoom;
import com.gdg.z_meet.domain.chat.entity.JoinChat;
import com.gdg.z_meet.domain.chat.entity.TeamChatRoom;
import com.gdg.z_meet.domain.chat.repository.ChatRoomRepository;
import com.gdg.z_meet.domain.chat.repository.JoinChatRepository;
import com.gdg.z_meet.domain.chat.repository.TeamChatRoomRepository;
import com.gdg.z_meet.domain.fcm.service.producer.FcmMessageProducer;
import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.global.exception.BusinessException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import com.gdg.z_meet.global.response.Code;

import java.util.List;
import java.util.Optional;
import java.util.stream.Collectors;


@Service
@RequiredArgsConstructor
@Slf4j
public class FcmChatMessageServiceImpl implements FcmChatMessageService {

    private final FcmMessageProducer fcmMessageProducer;
    private final ChatRoomRepository chatRoomRepository;
    private final TeamChatRoomRepository teamChatRoomRepository;
    private final JoinChatRepository joinChatRepository;


    @Override
    public void messagingChat(ChatMessageRes chatMessageRes) {
        Long roomId = chatMessageRes.getRoomId();
        Long senderId = chatMessageRes.getSenderId();
        String body = chatMessageRes.getContent();        // 채팅 내용 그대로 전달

        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(Code.CHATROOM_NOT_FOUND));

        String title = "";
        List<User> recipients = null;

        switch (chatRoom.getChatType()) {
            case USER -> {
                recipients = findRecipients(roomId, senderId);

                User opponent = recipients.stream().findFirst().orElse(null);
                if (opponent != null) {
                    title = opponent.getUserProfile().getNickname() + " (님)이 메시지를 보냈어요 💬";
                }
            }

            case TEAM -> {
                recipients = findRecipients(roomId, senderId);

                Optional<Team> opponentTeamOpt = teamChatRoomRepository.findOtherTeamInChatRoom(roomId, senderId);
                String teamName = opponentTeamOpt.map(Team::getName).orElse("");

                title = teamName + " 팀과의 채팅방에 메시지가 도착했어요 💬";
            }

            case RANDOM -> {
                recipients = findRecipients(roomId, senderId);

                TeamChatRoom teamChatRoom = teamChatRoomRepository.findFirstByChatRoomId(roomId)
                        .orElseThrow(() -> new BusinessException(Code.CHATROOM_NOT_FOUND));

                title = "[" + teamChatRoom.getName() + "] 채팅방에 메시지가 도착했어요 💬";
            }
        }

        if (recipients == null || recipients.isEmpty()) {
            log.warn("채팅방에 메시지 받을 사용자가 없습니다 - roomId: {}, senderId: {}", roomId, senderId);
            return;
        }

        // RabbitMQ를 통한 비동기 FCM 메시지 전송
        for (User user : recipients) {
            fcmMessageProducer.sendSingleMessage(user.getId(), title, body);
        }
        log.info("FCM 메시지를 큐에 전송했습니다 - roomId: {}, 총 대상: {}", roomId, recipients.size());
    }

    private List<User> findRecipients(Long roomId, Long senderId) {
        return joinChatRepository.findByChatRoomId(roomId).stream()
                .map(JoinChat::getUser)
                .filter(u -> !u.getId().equals(senderId))
                .collect(Collectors.toList());
    }

    @Override
    public void messagingOpenChatRoom(User user, Long roomId) {

        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(Code.CHATROOM_NOT_FOUND));

        String title = generateOpenChatTitle(user, chatRoom);
        String body = "두근두근💗 새로운 사람들과 인사부터 시작해보세요!";

        // RabbitMQ를 통한 비동기 FCM 메시지 전송
        fcmMessageProducer.sendSingleMessage(user.getId(), title, body);
        log.info("채팅방 열림 FCM 메시지를 큐에 전송했습니다 - userId: {}, roomId: {}", user.getId(), roomId);
    }

    private String generateOpenChatTitle(User user, ChatRoom chatRoom) {
        switch (chatRoom.getChatType()) {
            case USER -> {
                List<JoinChat> joinChats = joinChatRepository.findByChatRoomId(chatRoom.getId());

                return joinChats.stream()
                        .map(JoinChat::getUser)
                        .filter(u -> !u.getId().equals(user.getId()))
                        .findFirst()
                        .map(u -> u.getUserProfile().getNickname() + " 님과의 채팅방이 열렸어요! 🤗")
                        .orElse("채팅방이 열렸어요! 🤗");
            }

            case TEAM -> {
                return teamChatRoomRepository
                        .findOtherTeamInChatRoom(chatRoom.getId(), user.getId())
                        .map(team -> team.getName() + " 팀과의 채팅방이 열렸어요! 🤗")
                        .orElse("채팅방이 열렸어요! 🤗");
            }

            case RANDOM -> {
                return teamChatRoomRepository.findFirstByChatRoomId(chatRoom.getId())
                        .map(tcr -> tcr.getName() + " 채팅방이 열렸어요! 🤗")
                        .orElse("채팅방이 열렸어요! 🤗");
            }

            default -> {
                return "채팅방이 열렸어요! 🤗";
            }
        }
    }
}

