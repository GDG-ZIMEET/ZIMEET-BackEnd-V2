package com.gdg.z_meet.global.common;

import com.gdg.z_meet.domain.user.entity.User;
import org.springframework.security.core.context.SecurityContextHolder;
import org.springframework.stereotype.Component;

@Component
public class AuthenticatedUserUtils {

    private AuthenticatedUserUtils() {}

    public static String getAuthenticatedStudentNumber(){

        // 현재 인증된 사용자 정보 가져오기
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof User user) {    // Principal 의 객체 타입 확인
            return user.getStudentNumber();
        } else if (principal != null) {
            return principal.toString();
        } else {
            throw new IllegalArgumentException("현재 인증된 사용자를 찾을 수 없습니다.");
        }
    }

    public static Long getAuthenticatedUserId(){

        // 현재 인증된 사용자 정보 가져오기
        Object principal = SecurityContextHolder.getContext().getAuthentication().getPrincipal();

        if (principal instanceof User user) {    // Principal 의 객체 타입 확인
            return user.getId();
        } else {
            throw new IllegalArgumentException("현재 인증된 사용자를 찾을 수 없습니다.");
        }
    }
}
