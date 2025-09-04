package com.gdg.z_meet.domain.chat.service;

import com.gdg.z_meet.domain.chat.dto.ChatRoomDto;
import com.gdg.z_meet.domain.chat.entity.ChatRoom;
import com.gdg.z_meet.domain.chat.entity.JoinChat;
import com.gdg.z_meet.domain.chat.entity.Message;
import com.gdg.z_meet.domain.chat.entity.TeamChatRoom;
import com.gdg.z_meet.domain.chat.entity.status.ChatType;
import com.gdg.z_meet.domain.chat.entity.status.JoinChatStatus;
import com.gdg.z_meet.domain.chat.repository.ChatRoomRepository;
import com.gdg.z_meet.domain.chat.repository.JoinChatRepository;
import com.gdg.z_meet.domain.chat.repository.mongo.MongoMessageRepository;
import com.gdg.z_meet.domain.chat.repository.TeamChatRoomRepository;
import com.gdg.z_meet.domain.meeting.entity.Hi;
import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.meeting.entity.UserTeam;
import com.gdg.z_meet.domain.meeting.entity.enums.HiStatus;
import com.gdg.z_meet.domain.meeting.repository.HiRepository;
import com.gdg.z_meet.domain.meeting.repository.TeamRepository;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.meeting.service.HiCommandServiceImpl;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import jakarta.annotation.PostConstruct;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Pageable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class ChatRoomCommandService {

    private final RedisTemplate<String, Object> redisTemplate;
    private final ChatRoomRepository chatRoomRepository;
    private final MongoMessageRepository mongoMessageRepository;
    private final JoinChatRepository joinChatRepository;
    private final UserRepository userRepository;
    private final UserTeamRepository userTeamRepository;
    private final TeamChatRoomRepository teamChatRoomRepository;
    private final HiRepository hiRepository;
    private final ChatRoomQueryService chatRoomQueryService;

    private static final String CHAT_ROOMS_KEY = "chatrooms";
    private static final String CHAT_ROOM_ACTIVITY_KEY = "chatroom:activity";
    private final HiCommandServiceImpl hiCommandService;
    private final TeamRepository teamRepository;

    //레디스 초기화 : 랜덤채팅 최신id 저장
    @PostConstruct
    public void initRandomChatIdRedis() {
        String key = "chat:randomChatId";
        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            Long max = chatRoomRepository.findMaxRandomChatId().orElse(0L);
            redisTemplate.opsForValue().set(key, max);
        }
    }


    // 채팅방 삭제
    @Transactional
    public void deleteChatRoom(Long chatRoomId) {
        // 채팅방 존재 여부 확인
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(Code.CHATROOM_NOT_FOUND));

        int batchSize = 500;
        Pageable pageable = PageRequest.of(0, batchSize);

        while (true) {
            List<Message> messages = mongoMessageRepository.findByChatRoomId(String.valueOf(chatRoomId), pageable);
            if (messages.isEmpty()) {
                break; // 더 이상 삭제할 메시지가 없으면 종료
            }
            mongoMessageRepository.deleteAll(messages);
        }

        // 연관된 JoinChat 삭제
        List<JoinChat> joinChats = joinChatRepository.findByChatRoomId(chatRoomId);
        if (!joinChats.isEmpty()) {
            joinChats.forEach(joinChat -> {
                String joinChatsKey = "user:" + joinChat.getUser().getId() + ":chatrooms";
                redisTemplate.opsForSet().remove(joinChatsKey, chatRoomId);
            });
            joinChatRepository.deleteAll(joinChats);
        }

        // Redis에서 채팅방 삭제
        redisTemplate.opsForHash().delete(CHAT_ROOMS_KEY, chatRoomId.toString());

        // Redis의 활동 시간 데이터 삭제
        redisTemplate.opsForZSet().remove(CHAT_ROOM_ACTIVITY_KEY, chatRoomId.toString());

        // 채팅방 삭제
        chatRoomRepository.deleteById(chatRoomId);

    }

    // 팀으로 채팅방 추가
    @Transactional
    public ChatRoomDto.resultChatRoomDto addTeamJoinChat(ChatRoomDto.hiDto hiDto) {
        List<Long> teamIds = Arrays.asList(hiDto.getFromId(), hiDto.getToId());

        // 공통 메서드 호출하여 from, to 팀 할당
        Map<String, Team> teams = hiCommandService.assignEntities(
                teamRepository.findByIdIn(teamIds),
                hiDto.getFromId(),
                Team::getId
        );

        Team from = teams.get("from");
        Team to = teams.get("to");

        Hi hi = hiRepository.findByFromIdAndToIdAndHiStatus(from.getId(), to.getId(), HiStatus.NONE);
        if (hi == null) throw new BusinessException(Code.HI_NOT_FOUND);
        hi.setChangeStatus(HiStatus.ACCEPT);
        hiRepository.save(hi);

        //채팅방 생성
        ChatRoom chatRoom = ChatRoom.builder()
                .chatType(ChatType.TEAM)
                .build();
        chatRoom = chatRoomRepository.save(chatRoom);

        addTeamToChatRoom(chatRoom, from, to.getName());
        addTeamToChatRoom(chatRoom, to, from.getName());

        return ChatRoomDto.resultChatRoomDto.builder()
                .chatRoomid(chatRoom.getId())
                .build();
    }

    // 사용자 채팅방 추가
    @Transactional
    public ChatRoomDto.resultChatRoomDto addUserJoinChat(ChatRoomDto.hiDto hiDto) {
        List<Long> userIds = Arrays.asList(hiDto.getFromId(), hiDto.getToId());

        // 공통 메서드 호출하여 from, to 팀 할당
        Map<String, User> users = hiCommandService.assignEntities(
                userRepository.findByIdIn(userIds),
                hiDto.getFromId(),
                User::getId
        );
        User from = users.get("from");
        User to = users.get("to");

        Hi hi = hiRepository.findByFromIdAndToIdAndHiStatus(from.getId(), to.getId(), HiStatus.NONE);
        if (hi == null) throw new BusinessException(Code.HI_NOT_FOUND);
        hi.setChangeStatus(HiStatus.ACCEPT);
        hiRepository.save(hi);

        //채팅방 생성
        ChatRoom chatRoom = ChatRoom.builder()
                .chatType(ChatType.USER)
                .build();
        chatRoom = chatRoomRepository.save(chatRoom);

        List<User> userList = users.values().stream().collect(Collectors.toList());
        addUserToChatRoom(chatRoom, userList);

        return ChatRoomDto.resultChatRoomDto.builder()
                .chatRoomid(chatRoom.getId())
                .build();
    }

    private Long getNewRandomChatId() {
        String key = "chat:randomChatId";

        if (Boolean.FALSE.equals(redisTemplate.hasKey(key))) {
            redisTemplate.opsForValue().set(key, 0);
        }

        return redisTemplate.opsForValue().increment(key);
    }

    public ChatRoomDto.resultChatRoomDto addRandomUserJoinChat(List<Long> userIds){
        if(userIds.size() != 4)
            throw new BusinessException(Code.RANDOM_MEETING_USER_COUNT);

        // Redis에서 auto-increment된 randomChatId 가져오기
        Long newRandomChatId = getNewRandomChatId();

        //채팅방 생성
        ChatRoom chatRoom = ChatRoom.builder()
                .chatType(ChatType.RANDOM)
                .randomChatId(newRandomChatId)
                .build();
        chatRoom = chatRoomRepository.save(chatRoom);

        List<User> users = userRepository.findAllById(userIds);
        if(users.size() < userIds.size())//저장 안된 경우 에러처리
            throw new BusinessException(Code.RANDOM_MEETING_USER_COUNT);
        addUserToChatRoom(chatRoom, users);

        return ChatRoomDto.resultChatRoomDto.builder()
                .chatRoomid(chatRoom.getId())
                .build();
    }

    // 사용자 채팅방 추가
    @Transactional
    public void addUserToChatRoom(ChatRoom chatRoom, List<User> users){
        Long chatRoomId = chatRoom.getId();

        // 새로운 사용자만 필터링하여 추가
        List<JoinChat> newJoinChats = new ArrayList<>();
        for (User user : users) {
            Long userId = user.getId();

            // Redis에 참여 여부가 있는지 먼저 체크
            String joinChatsKey = "user:" + userId + ":chatrooms";
              // 새로운 User 정보 DB 저장 (배치 인서트로 한 번에 저장)
            newJoinChats.add(JoinChat.builder()
                    .user(user)
                    .chatRoom(chatRoom)
                    .status(JoinChatStatus.ACTIVE)
                    .build());

            // 사용자 -> 참여 채팅방 매핑 데이터 Redis 저장
            redisTemplate.opsForSet().add(joinChatsKey, String.valueOf(chatRoomId));

            // 채팅방 -> 참여 사용자 매핑 데이터 Redis 저장
            String chatRoomUsersKey = "chatroom:" + chatRoomId + ":users";
            redisTemplate.opsForSet().add(chatRoomUsersKey, String.valueOf(userId));

        }

        // 한번에 저장
        if (!newJoinChats.isEmpty()) {
            joinChatRepository.saveAll(newJoinChats);
        }
    }

    @Transactional
    public void addTeamToChatRoom(ChatRoom chatRoom, Team team, String teamName){

        List<UserTeam> userTeams = userTeamRepository.findByTeamId(team.getId());
        List<User> users = userTeams.stream()
                .map(UserTeam::getUser)
                .collect(Collectors.toList());

        // 팀정보 DB 저장
        TeamChatRoom teamChatRoom = TeamChatRoom.builder()
                .team(team)
                .chatRoom(chatRoom)
                .name(teamName)
                .build();

        teamChatRoomRepository.save(teamChatRoom);

        // 사용자 저장
        addUserToChatRoom(chatRoom, users);
    }

    // 사용자 제거
    @Transactional
    public void removeUserFromChatRoom(Long chatRoomId, Long userId) {

        // DB에서 제거
        User user = userRepository.findById(userId).orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
        ChatRoom chatRoom = chatRoomQueryService.getChatRoomById(chatRoomId);

        JoinChat joinChat = joinChatRepository.findByUserAndChatRoom(user, chatRoom)
                .orElseThrow(() ->  new BusinessException(Code.JOINCHAT_NOT_FOUND));

        joinChat.leaveChat();
        joinChatRepository.save(joinChat);


        String joinChatsKey = "user:" + userId + ":chatrooms";
        String chatRoomUsersKey = "chatroom:" + chatRoomId + ":users";

        // 사용자와 채팅방 간 매핑 데이터 제거 (Redis)
        redisTemplate.opsForSet().remove(joinChatsKey, String.valueOf(chatRoomId));
        redisTemplate.opsForSet().remove(chatRoomUsersKey, String.valueOf(userId));

        // 채팅방에 남은 사용자가 없다면 Redis에서 해당 채팅방 삭제
        if (Boolean.FALSE.equals(redisTemplate.opsForSet().size(chatRoomUsersKey) > 0)) {
            redisTemplate.opsForHash().delete(CHAT_ROOMS_KEY, chatRoomId.toString());
            redisTemplate.opsForZSet().remove(CHAT_ROOM_ACTIVITY_KEY, chatRoomId.toString());
        }

    }

    //사용자의 모든 채팅방 나가기
    @Transactional
    public void removeUser(Long userId) {
        // DB에서 사용자 상태를 INACTIVE로 변경
        joinChatRepository.deleteJoinChatWithUser(userId);

        // Redis에서 해당 사용자가 속한 모든 채팅방과 관련된 데이터를 한 번에 삭제
        String joinChatsKey = "user:" + userId + ":chatrooms";
        Set<Object> chatRoomIdsSet = redisTemplate.opsForSet().members(joinChatsKey);

        if (chatRoomIdsSet != null && !chatRoomIdsSet.isEmpty()) {
            Set<String> chatRoomIds = chatRoomIdsSet.stream()
                    .map(Object::toString)
                    .collect(Collectors.toSet());
            // 여러 채팅방에 대한 사용자 정보 삭제
            for (String chatRoomId : chatRoomIds) {
                String chatRoomUsersKey = "chatroom:" + chatRoomId + ":users";

                // Redis에서 사용자와 채팅방 간 매핑 데이터 제거
                redisTemplate.opsForSet().remove(joinChatsKey, chatRoomId);
                redisTemplate.opsForSet().remove(chatRoomUsersKey, String.valueOf(userId));

                // 채팅방에 남은 사용자가 없다면 해당 채팅방 삭제
                Long remainingUsers = redisTemplate.opsForSet().size(chatRoomUsersKey);
                if (remainingUsers != null && remainingUsers == 0) {
                    redisTemplate.opsForHash().delete(CHAT_ROOMS_KEY, chatRoomId);
                    redisTemplate.opsForZSet().remove(CHAT_ROOM_ACTIVITY_KEY, chatRoomId);
                }
            }
        }

        // Redis에서 사용자 채팅방 목록 삭제
        redisTemplate.delete(joinChatsKey);
    }

}
