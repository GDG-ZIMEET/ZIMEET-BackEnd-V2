package com.gdg.z_meet.domain.user.dto;

import com.gdg.z_meet.domain.user.entity.enums.*;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Size;
import lombok.*;
import org.hibernate.validator.constraints.Range;

public class UserReq {
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class LoginReq{
        @NotNull(message = "학번을 입력해주세요.")
        private String studentNumber;
        @NotNull(message = "비밀번호를 입력해주세요.")
        private String password;
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class SignUpReq{
        @NotNull(message = "이름 입력은 필수입니다.")
        private String name;
        @NotNull(message = "학번 입력은 필수입니다.")
        private String studentNumber;
        @NotNull(message = "비밀번호 입력은 필수입니다.")
        private String password;
        @NotNull(message = "전화번호 입력은 필수입니다.")
        private String phoneNumber;

        //유저 상세정보
        @NotNull(message = "닉네임 입력은 필수입니다.")
        @Size(min = 2, max = 7, message = "닉네임은 2자 이상 7자 이하로 입력해주세요.")
        private String nickname;
        private Grade grade;
        @Range(min = 19, max = 28, message = "나이는 19세 이상 28세 이하로 입력해주세요.")
        private int age;
        private Gender gender;
        private Major major;
        private String emoji;
        private MBTI mbti;
        private Music music;
        private Style style;
        private IdealAge idealAge;
        private IdealType idealType;
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class NicknameUpdateReq {
        @Size(min = 2, max = 7, message = "닉네임은 2자 이상 7자 이하로 입력해주세요.")
        private String nickname;
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class EmojiUpdateReq{
        private String emoji;
    }

    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    @Getter
    @Setter
    public static class ResetPasswordReq{
        private String name;
        private String studentNumber;
        private String phoneNumber;
        private String newPassword;
        private String confirmPassword;
    }

    @Builder
    @Getter
    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class pushAgreeReq{
        boolean pushAgree;   // T/F
    }

    @Builder
    @Getter
    @AllArgsConstructor(access = AccessLevel.PROTECTED)
    @NoArgsConstructor(access = AccessLevel.PROTECTED)
    public static class saveFcmTokenReq{
        String fcmToken;
    }
}
