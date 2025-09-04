package com.gdg.z_meet.domain.chat.service;

import com.gdg.z_meet.domain.chat.dto.ChatRoomDto;
import com.gdg.z_meet.domain.chat.entity.ChatRoom;
import com.gdg.z_meet.domain.chat.entity.JoinChat;
import com.gdg.z_meet.domain.chat.entity.TeamChatRoom;
import com.gdg.z_meet.domain.chat.entity.status.ChatType;
import com.gdg.z_meet.domain.chat.entity.status.JoinChatStatus;
import com.gdg.z_meet.domain.chat.repository.ChatRoomRepository;
import com.gdg.z_meet.domain.chat.repository.JoinChatRepository;
import com.gdg.z_meet.domain.chat.repository.TeamChatRoomRepository;
import com.gdg.z_meet.domain.meeting.entity.UserTeam;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.entity.enums.Gender;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.*;
import java.util.function.Function;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
@Transactional
public class ChatRoomQueryService {

    private final ChatRoomRepository chatRoomRepository;
    private final JoinChatRepository joinChatRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final TeamChatRoomRepository teamChatRoomRepository;
    private final UserTeamRepository userTeamRepository;
    private final RedisTemplate<String, Object> redisTemplate;

    private static final String CHAT_ROOM_LATEST_MESSAGE_KEY = "chatroom:%s:latestMessage";

    // 채팅방 ID로 채팅방 조회 (Redis 캐싱)
    public ChatRoom getChatRoomById(Long chatRoomId) {
        Object chatRoomObj = redisTemplate.opsForHash().get("chatrooms", chatRoomId.toString());
        if (chatRoomObj instanceof ChatRoom) {
            return (ChatRoom) chatRoomObj;
        }
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(Code.CHATROOM_NOT_FOUND));
        redisTemplate.opsForHash().put("chatrooms", chatRoomId.toString(), chatRoom);
        return chatRoom;
    }

    // 사용자 채팅방 목록 조회 (최신 활동 기준 정렬)
    @Transactional
    public List<ChatRoomDto.chatRoomListDto> getChatRoomsByUser(Long userId) {
        String joinChatsKey = "user:" + userId + ":chatrooms";

        // Redis에서 참여 채팅방 ID 가져오기 및 변환
        Set<Long> joinChatIdsSet = redisTemplate.opsForSet()
                .members(joinChatsKey)
                .stream()
                .map(Object::toString)
                .map(Long::valueOf)
                .collect(Collectors.toSet());


        if (joinChatIdsSet == null || joinChatIdsSet.isEmpty()) {
            List<JoinChat> joinChats = joinChatRepository.findByUserIdAndStatus(userId);
            joinChatIdsSet = joinChats.stream()
                    .map(joinChat -> joinChat.getChatRoom().getId())
                    .collect(Collectors.toSet());
            joinChatIdsSet.forEach(chatRoomId -> redisTemplate.opsForSet().add(joinChatsKey, chatRoomId));
        }


        // Redis ZSet에서 최신 활동 기준으로 정렬된 채팅방 ID 가져오기
        List<Long> sortedChatRoomIds = Optional.ofNullable(
                        redisTemplate.opsForZSet().reverseRange(CHAT_ROOM_LATEST_MESSAGE_KEY, 0, -1))
                .orElse(Set.of()) // null 방지
                .stream()
                .map(Object::toString)
                .map(Long::valueOf)
                .filter(joinChatIdsSet::contains)
                .collect(Collectors.toList());

        // 정렬된 목록에 없는 채팅방 추가
        joinChatIdsSet.stream()
                .filter(id -> !sortedChatRoomIds.contains(id)) // 정렬되지 않은 채팅방 추가
                .forEach(sortedChatRoomIds::add);


        return sortedChatRoomIds.stream()
                .map(chatRoomId -> {

                    ChatRoom chatRoom = getChatRoomById(chatRoomId);
                    String chatRoomName = getChatRoomName(userId, chatRoom.getId());

                    // 최신 메시지 및 메시지 시간 조회
                    String latestMessageKey = String.format(CHAT_ROOM_LATEST_MESSAGE_KEY, chatRoomId);
                    String latestMessageTimeKey = String.format("chatroom:%s:latestMessageTime", chatRoomId);

                    String latestMessage = (String) redisTemplate.opsForValue().get(latestMessageKey);
                    String latestMessageTimeStr = (String) redisTemplate.opsForValue().get(latestMessageTimeKey);

                    LocalDateTime latestMessageTime = null;
                    if (latestMessageTimeStr != null) {
                        latestMessageTime = LocalDateTime.parse(latestMessageTimeStr);
                    }
                    else{
                        latestMessageTime = chatRoom.getUpdatedAt();
                    }

                    List<ChatRoomDto.UserProfileDto> userProfiles = getUserProfilesByChatRoomId(userId, chatRoomId, true);


                    return new ChatRoomDto.chatRoomListDto(chatRoom.getId(), chatRoomName, latestMessage, latestMessageTime, userProfiles);
                })
                .sorted(Comparator.comparing(ChatRoomDto.chatRoomListDto::getLastestTime).reversed()) // 최신 시간 기준으로 정렬
                .collect(Collectors.toList());
    }


    // 채팅방 이름 조회
    @Transactional
    public String getChatRoomName(Long userId, Long chatRoomId){
        ChatRoom chatRoom = chatRoomRepository.findById(chatRoomId)
                .orElseThrow(() -> new BusinessException(Code.CHATROOM_NOT_FOUND));

        if (chatRoom.getChatType() == ChatType.RANDOM) {
            // 랜덤 채팅방이면 고유한 ID 기반으로 이름 생성
            return "랜덤채팅 " + (chatRoom.getRandomChatId() +30) + "번방";
        }

        if (chatRoom.getChatType() == ChatType.USER) {//사용자 채팅방일 경우 상대방 이름 반환
            JoinChat joinChat = joinChatRepository.findUserByChatRoomIdAndUserNotUserId(chatRoomId, userId)
                                                    .orElseThrow(() -> new BusinessException(Code.JOINCHAT_NOT_FOUND));
            User user = joinChat.getUser();
            return user.getUserProfile().getNickname();
        }

        // 해당 채팅방의 팀 조회
        List<TeamChatRoom> teamChatRooms = teamChatRoomRepository.findByChatRoomId(chatRoomId);

        // 현재 사용자가 속한 팀 찾기
        TeamChatRoom userTeamChatRoom = teamChatRooms.stream()
                .filter(teamChatRoom ->
                        userTeamRepository.existsByUserIdAndTeamId(userId, teamChatRoom.getTeam().getId()))
                .findFirst()
                .orElseThrow(() -> new BusinessException(Code.JOINCHAT_NOT_FOUND));

        // 상대팀 이름 리턴
        return userTeamChatRoom.getName();
    }

    @Transactional
    public List<ChatRoomDto.UserProfileDto> getUserProfilesByChatRoomId(Long userId, Long chatRoomId, boolean filterByGender) {
        // 현재 사용자의 성별 조회
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));
        Gender userGender = user.getUserProfile().getGender(); // 성별 가져오기

        // 채팅방 전체 사용자 조회
        List<User> users = joinChatRepository.findUsersByChatRoomId(chatRoomId);

        // filterByGender가 true일 경우, 이성만 남김
        if (filterByGender) {
            users = users.stream()
                    .filter(nowUser -> !nowUser.getUserProfile().getGender().equals(userGender)) // 성별이 다를 때만 포함
                    .collect(Collectors.toList());
        }

        List<UserProfile> userProfiles = userProfileRepository.findByUserIn(users);

        return userProfiles.stream()
                .map(userProfile -> {
                    User chatUser = userProfile.getUser();
                    UserProfile profile = chatUser.getUserProfile();

                    String nickname = (profile != null && profile.getNickname() != null && !profile.getNickname().isBlank())
                            ? profile.getNickname()
                            : "User";

                    String userName = chatUser.getId().equals(userId)
                            ? nickname + " (나)"
                            : nickname;

                    return new ChatRoomDto.UserProfileDto(
                            chatUser.getId(),
                            userName,
                            userProfile.getEmoji(),
                            userProfile.getGender()
                    );
                })
                .collect(Collectors.toList());
    }

    //채팅방에 있는 사용자 조회
    @Transactional
    public List<ChatRoomDto.chatRoomUserList> getUserByRoomId(Long userId, Long roomId) {
        // 사용자가 해당 채팅방에 존재하는지 확인
        if (!joinChatRepository.existsByUserIdAndChatRoomIdAndStatusActive(userId, roomId)) {
            throw new BusinessException(Code.JOINCHAT_NOT_FOUND);
        }

        // 채팅방 정보 조회
        ChatRoom chatRoom = chatRoomRepository.findById(roomId)
                .orElseThrow(() -> new BusinessException(Code.CHATROOM_NOT_FOUND));

        // 채팅방이 TEAM 타입인 경우 팀으로 사용자 구분
        if (chatRoom.getChatType() == ChatType.TEAM) {
            return getTeamChatRoomUserList(userId, roomId);
        }
        return getRandomChatRoomUserList(userId, roomId);



    }

    private List<ChatRoomDto.chatRoomUserList> getTeamChatRoomUserList(Long userId, Long roomId) {
        List<ChatRoomDto.UserProfileDto> userProfileDtos = getUserProfilesByChatRoomId(userId, roomId, false);

        List<TeamChatRoom> teamChatRooms = teamChatRoomRepository.findByChatRoomId(roomId);
        List<Long> teamIds = teamChatRooms.stream()
                .map(teamChatRoom -> teamChatRoom.getTeam().getId())
                .collect(Collectors.toList());

        List<UserTeam> userTeams = userTeamRepository.findByTeamIdIn(teamIds);

        // 유저 프로필 맵으로 전환
        Map<Long, ChatRoomDto.UserProfileDto> userProfileMap = userProfileDtos.stream()
                .collect(Collectors.toMap(ChatRoomDto.UserProfileDto::getUserId, Function.identity()));

        // 팀별 유저 리스트 맵핑
        Map<Long, List<Long>> teamUserMap = userTeams.stream()
                .collect(Collectors.groupingBy(
                        userTeam -> userTeam.getTeam().getId(),
                        Collectors.mapping(userTeam -> userTeam.getUser().getId(), Collectors.toList())
                ));

        List<ChatRoomDto.chatRoomUserList> teamUserLists = new ArrayList<>();
        Long myTeamId = userTeams.stream()
                .filter(userTeam -> userTeam.getUser().getId().equals(userId))
                .map(userTeam -> userTeam.getTeam().getId())
                .findFirst()
                .orElse(null); // 현재 사용자의 팀 ID

        for (TeamChatRoom teamChatRoom : teamChatRooms) {
            Long teamId = teamChatRoom.getTeam().getId();

            // 상대방 팀의 이름 추출
            String teamName = teamChatRooms.stream()
                    .filter(tc -> !tc.getTeam().getId().equals(teamId))  // 현재 teamChatRoom이 아닌 것 선택
                    .map(TeamChatRoom::getName)  // 이름 추출
                    .findFirst()  // 상대방 이름 가져오기
                    .orElse("알 수 없는");  // 혹시라도 2개가 아닐 경우 대비

            List<Long> teamUserIds = teamUserMap.getOrDefault(teamId, Collections.emptyList());
            List<ChatRoomDto.UserProfileDto> teamUsers = teamUserIds.stream()
                    .map(userProfileMap::get)
                    .filter(Objects::nonNull)
                    .collect(Collectors.toList());

            teamUserLists.add(ChatRoomDto.chatRoomUserList.builder()
                    .teamId(teamId)
                    .teamName(teamName)
                    .userProfiles(teamUsers)
                    .build());
        }
        //우리팀을 첫번째로 배치
        if (myTeamId != null) {
            teamUserLists.sort(Comparator.comparing(team -> !team.getTeamId().equals(myTeamId)));
        }

        return teamUserLists;
    }

    private List<ChatRoomDto.chatRoomUserList> getRandomChatRoomUserList(Long userId, Long roomId) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Code.MEMBER_NOT_FOUND));

        // 채팅방에 속한 사용자 프로필 가져오기
        List<ChatRoomDto.UserProfileDto> userProfileDtos = getUserProfilesByChatRoomId(userId, roomId, false);

        // 성별에 따른 이성팀과 내팀 분리
        List<ChatRoomDto.UserProfileDto> myTeamUsers = userProfileDtos.stream()
                .filter(profile -> profile.getGender() == user.getUserProfile().getGender()) // 내 성별 팀
                .collect(Collectors.toList());

        List<ChatRoomDto.UserProfileDto> anotherTeamUsers = userProfileDtos.stream()
                .filter(profile -> profile.getGender() != user.getUserProfile().getGender()) // 이성 팀
                .collect(Collectors.toList());

        List<ChatRoomDto.chatRoomUserList> sortedUserLists = new ArrayList<>();

        sortedUserLists.add(ChatRoomDto.chatRoomUserList.builder()
                .teamId(null)
                .teamName("내 ") // 내 팀을 항상 첫 번째로 배치
                .userProfiles(myTeamUsers)
                .build());

        sortedUserLists.add(ChatRoomDto.chatRoomUserList.builder()
                .teamId(null)
                .teamName("이성 ")
                .userProfiles(anotherTeamUsers)
                .build());

        return sortedUserLists;
    }
}
