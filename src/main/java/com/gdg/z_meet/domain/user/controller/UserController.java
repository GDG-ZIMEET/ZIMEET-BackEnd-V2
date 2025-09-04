package com.gdg.z_meet.domain.user.controller;

import com.gdg.z_meet.global.common.AuthenticatedUserUtils;
import com.gdg.z_meet.global.jwt.JwtUtil;
import com.gdg.z_meet.global.response.Response;
import com.gdg.z_meet.domain.user.dto.Token;
import com.gdg.z_meet.domain.user.dto.UserReq;
import com.gdg.z_meet.domain.user.dto.UserRes;
import com.gdg.z_meet.domain.user.service.UserService;
import com.gdg.z_meet.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@Tag(name = "User", description = "User API")
@RequestMapping("/api/user")
@Slf4j
public class UserController {
    private final UserService userService;
    private final JwtUtil jwtUtil;

    @PostMapping("/signup")
    @Operation(summary = "회원가입", description = "회원가입")
    public Response<UserRes.SignUpRes> signup(@Valid @RequestBody UserReq.SignUpReq signUpReq) {
        return Response.ok(userService.signup(signUpReq));
    }

    @PostMapping("/login")
    @Operation(summary = "로그인", description = "로그인")
    public Response<UserRes.LoginRes> login(@Valid @RequestBody UserReq.LoginReq loginReq, HttpServletResponse response) {
        Token token = userService.login(loginReq, response);
        log.info("User logged in: studentNumber={}", loginReq.getStudentNumber());
        return Response.ok(UserRes.LoginRes.fromToken(token));
    }

    @DeleteMapping("/logout")
    @Operation(summary = "로그아웃", description = "로그아웃")
    public Response<Void> logout(@RequestHeader(value = "X-FCM-TOKEN", required = false) String fcmToken, HttpServletRequest request, HttpServletResponse response) {
        userService.logout(request, response, fcmToken);
        return Response.ok();
    }

    @GetMapping("/myprofile")
    @Operation(summary = "내 세부 프로필 조회", description = "내 세부 프로필 조회")
    public Response<UserRes.ProfileRes> getProfile(HttpServletRequest request) {
        Long userId = jwtUtil.extractUserIdFromRequest(request);
        return Response.ok(userService.getProfile(userId));
    }

    @GetMapping("/profile/{nickname}")
    @Operation(summary = "유저 프로필 조회", description = "유저 프로필 조회")
    public Response<UserRes.UserProfileRes> getProfile(HttpServletRequest request, @PathVariable String nickname) {

        Long userId = jwtUtil.extractUserIdFromRequest(request);
        return Response.ok(userService.getUserProfile(userId, nickname));
    }

    @PatchMapping("/myprofile/nickname")
    @Operation(summary = "내 닉네임 수정", description = "내 닉네임 수정")
    public Response<UserRes.NicknameUpdateRes> updateNickname(
            HttpServletRequest request,
            @Valid @RequestBody UserReq.NicknameUpdateReq nicknameUpdateReq) {
        Long userId = jwtUtil.extractUserIdFromRequest(request);
        return Response.ok(userService.updateNickname(userId, nicknameUpdateReq));
    }

    @PatchMapping("/myprofile/emoji")
    @Operation(summary = "내 이모지 수정", description = "내 이모지 수정")
    public Response<UserRes.EmojiUpdateRes> updateEmoji(
            HttpServletRequest request,
            @Valid @RequestBody UserReq.EmojiUpdateReq emojiUpdateReq) {
        Long userId = jwtUtil.extractUserIdFromRequest(request);
        return Response.ok(userService.updateEmoji(userId, emojiUpdateReq));
    }

    @PatchMapping("/withdraw")
    @Operation(summary = "회원탈퇴", description = "회원탈퇴")
    public Response<Void> withdraw(HttpServletRequest request, HttpServletResponse response) {
        Long userId = jwtUtil.extractUserIdFromRequest(request);
        userService.withdraw(userId, request, response);
        return Response.ok(null);
    }

    @GetMapping("/check/student-number")
    @Operation(summary = "학번 중복 확인", description = "학번 중복 확인")
    public Response<UserRes.DuplicateCheckRes> checkStudentNumber(@RequestParam String value) {
        boolean isDuplicate = userService.isStudentNumberDuplicate(value);
        return Response.ok(UserRes.DuplicateCheckRes.ofStudentNumber(isDuplicate));
    }

    @GetMapping("/check/phone-number")
    public Response<UserRes.DuplicateCheckRes> checkPhoneNumber(@RequestParam String value) {
        boolean isDuplicate = userService.isPhoneNumberDuplicate(value);
        return Response.ok(UserRes.DuplicateCheckRes.ofPhoneNumber(isDuplicate));
    }

    @GetMapping("/check/nickname")
    public Response<UserRes.DuplicateCheckRes> checkNickname(@RequestParam String value) {
        boolean isDuplicate = userService.isNicknameDuplicate(value);
        return Response.ok(UserRes.DuplicateCheckRes.ofNickname(isDuplicate));
    }

    @GetMapping("/check-login")
    public Response<UserRes.CheckLoginRes> checkLogin(HttpServletRequest request) {
        String accessToken = jwtUtil.getAccessToken(request);
        if (accessToken != null && jwtUtil.validateToken(request, accessToken)) {
            Long userId = jwtUtil.extractUserIdFromRequest(request);
            return Response.ok(UserRes.CheckLoginRes.loggedIn(userId, accessToken));
        }
        return Response.ok(UserRes.CheckLoginRes.loggedOut());
    }

    @PostMapping("/reset")
    @Operation(summary = "비밀번호 재설정", description = "비밀번호 재설정")
    public Response<?> resetPassword(@RequestBody UserReq.ResetPasswordReq resetPasswordReq) {
        UserRes.UpdatePasswordRes updatePasswordRes = userService.resetPassword(
                resetPasswordReq.getName(),
                resetPasswordReq.getStudentNumber(),
                resetPasswordReq.getPhoneNumber(),
                resetPasswordReq.getNewPassword(),
                resetPasswordReq.getConfirmPassword()
        );
        return Response.ok(updatePasswordRes);
    }

    @PostMapping("/increase/{nickname}")
    @Operation(summary = "하이, 티켓 개수 증가", description = "하이, 티켓 개수 증가")
    public Response<UserRes.IncreaseRes> increaseHiTicket(@PathVariable String nickname) {
        return Response.ok(userService.increaseHiTicket(nickname));
    }
}