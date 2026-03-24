package com.gdg.z_meet.domain.chat.dto;

import com.gdg.z_meet.domain.user.entity.enums.Gender;
import lombok.*;

import java.time.LocalDateTime;
import java.util.List;

public class ChatRoomDto {

    @Getter
    @AllArgsConstructor
    @Builder
    public static class resultChatRoomDto{
        private Long chatRoomid;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class hiDto {
        Long toId;
        Long fromId;
    }


    @Getter
    @AllArgsConstructor
    @Builder
    public static class chatRoomListDto{
        private Long chatRoomId;
        private String chatRoomName;
        private String latestMessage;    //최신 메시지
        private LocalDateTime lastestTime; //시간
        private List<UserProfileDto> userProfiles; // 사용자 프로필 목록
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class UserProfileDto {
        private Long userId;
        private String name;
        private String emoji;
        private Gender gender;

    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class TeamListDto{
        private Long teamId1;
        private Long teamId2;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class chatRoomMessageDTO{
        private Long chatRoomId;
        private String latestMessage;
        private LocalDateTime lastestTime;
    }

    @Getter
    @AllArgsConstructor
    @Builder
    public static class chatRoomUserList{
        private Long teamId;
        private String teamName;
        private List<UserProfileDto> userProfiles;
    }


}
