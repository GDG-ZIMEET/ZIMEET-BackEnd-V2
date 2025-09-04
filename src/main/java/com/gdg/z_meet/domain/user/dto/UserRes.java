package com.gdg.z_meet.domain.user.dto;

import com.gdg.z_meet.domain.user.entity.enums.*;
import lombok.*;

import java.util.List;

public class UserRes {
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class SignUpRes {
        private String message;
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class LoginRes {
        private String accessToken;
        private String key;
        private Long userId;

        public static LoginRes fromToken(Token token) {
            return new LoginRes(token.getAccessToken(), token.getKey(), token.getUserId());
        }
    }

    @Getter
    @Builder
    public static class ProfileRes {
        private Long id;
        private String name;
        private String studentNumber;
        private String phoneNumber;
        //유저 상세정보
        private String nickname;
        private Grade grade;
        private Gender gender;
        private int age;
        private Major major;
        private String emoji;
        private MBTI mbti;
        private Music music;
        private Style style;
        private IdealAge idealAge;
        private IdealType idealType;
        private Level level;
        private ProfileStatus profileStatus;
    }

    @Getter
    @Builder
    public static class UserProfileRes {
        private String nickname;
        private String studentNumber;
        private Gender gender;
        private String emoji;
        private int age;
        private String major;
        private MBTI mbti;
        private Style style;
        private IdealType idealType;
        private IdealAge idealAge;
        private Music music;
        private String level;
    }

    @Getter
    @Builder
    public static class NicknameUpdateRes {
        private String nickname;
    }

    @Getter
    @Builder
    public static class EmojiUpdateRes {
        private String emoji;
    }

    @Getter
    @Builder
    public static class CheckLoginRes {
        private Long id;
        private boolean isLoggedIn;
        private String accessToken;

        public static CheckLoginRes loggedIn(Long id, String accessToken) {
            return CheckLoginRes.builder()
                    .id(id)
                    .isLoggedIn(true)
                    .accessToken(accessToken)
                    .build();
        }

        public static CheckLoginRes loggedOut() {
            return CheckLoginRes.builder()
                    .id(null)
                    .isLoggedIn(false)
                    .accessToken(null)
                    .build();
        }

        public static CheckLoginRes refreshed(Long id, String newAccessToken) {
            return CheckLoginRes.builder()
                    .id(id)
                    .isLoggedIn(true)
                    .accessToken(newAccessToken)
                    .build();
        }
    }

    @Getter
    @Builder
    public static class DuplicateCheckRes {
        private boolean isDuplicated;
        private String message;

        public DuplicateCheckRes(boolean isDuplicated, String message) {
            this.isDuplicated = isDuplicated;
            this.message = message;
        }
        public static DuplicateCheckRes ofStudentNumber(boolean isDuplicated) {
            return DuplicateCheckRes.builder()
                    .isDuplicated(isDuplicated)
                    .message(isDuplicated ? "이미 사용 중인 학번입니다." : "사용 가능한 학번입니다.")
                    .build();
        }

        public static DuplicateCheckRes ofPhoneNumber(boolean isDuplicated) {
            return DuplicateCheckRes.builder()
                    .isDuplicated(isDuplicated)
                    .message(isDuplicated ? "이미 사용 중인 전화번호입니다." : "사용 가능한 전화번호입니다.")
                    .build();
        }

        public static DuplicateCheckRes ofNickname(boolean isDuplicated) {
            return DuplicateCheckRes.builder()
                    .isDuplicated(isDuplicated)
                    .message(isDuplicated ? "이미 사용 중인 닉네임입니다." : "사용 가능한 닉네임입니다.")
                    .build();
        }
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetLevelDTO {
        Long userId;
        String level;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UpdatePasswordRes {
        private String message;
    }

    @Getter
    @Setter
    @Builder
    public static class IncreaseRes {
        private String message;
    }
}
