package com.gdg.z_meet.domain.meeting.dto;

import com.gdg.z_meet.domain.meeting.entity.enums.HiType;
import com.gdg.z_meet.domain.user.entity.enums.*;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class MeetingResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetPreTeamDTO {
        Long teamId;
        List<String> emoji;
        String name;
        Integer verification;
        List<String> major;
        Double age;
        List<String> music;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetTeamGalleryDTO {
        List<GetPreTeamDTO> teamList;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetTeamUserDTO {
        Long userId;
        String emoji;
        String nickname;
        Integer age;
        String studentNumber;
        String major;
        String music;

        // 지밋 플러스
        String mbti;
        String style;
        String idealType;
        String idealAge;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetTeamDTO {
        Long teamId;
        String name;
        Integer verification;
        String gender;
        List<GetTeamUserDTO> userList;
        Boolean hi;
        String level;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetPreMyTeamDTO {
        Long teamId;
        List<String> emoji;
        String name;
        Integer verification;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetMyTeamDTO {
        Long teamId;
        String name;
        Integer verification;
        String gender;
        List<GetTeamUserDTO> userList;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetMyTeamHiDTO {
        Integer hi;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CheckNameDTO {
        Integer check;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class hiListDto {
        Long myTeamId;
        Long teamId;
        String teamName;
        List<UserProfileDto> userProfileDtos;
        Double age;
        String dateTime;
        HiType type; //USER or TEAM

        @Getter
        @AllArgsConstructor
        @Builder
        public static class UserProfileDto {
            private Major major;
            private String emoji;
            private Music music;
        }
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetSearchDTO {
        Long userId;
        String nickname;
        String major;
        String grade;
        String phoneNumber;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetSearchListDTO {
        List<GetSearchDTO> searchList;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetMyDeleteDTO {
        Integer leftDelete;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetVerificationTeamDTO {
        Long teamId;
        Integer verification;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetVerificationDTO {
        List<GetVerificationTeamDTO> teamList;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetPreUserDTO {
        Long userId;
        String emoji;
        String nickname;
        Integer verification;
        String major;
        Integer age;
        String music;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetUserGalleryDTO {
        List<GetPreUserDTO> userList;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetPreMyProfileDTO {
        Long userId;
        String emoji;
        String nickname;
        Integer verification;
        String profileStatus;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetMyHiDTO {
        Integer hi;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetProfileDTO {
        Long userId;
        String emoji;
        String nickname;
        Integer verification;
        Gender gender;
        Integer age;
        String studentNumber;
        String major;
        Music music;
        MBTI mbti;
        Style style;
        IdealType idealType;
        IdealAge idealAge;
        Boolean hi;
        Level level;
    }
}
