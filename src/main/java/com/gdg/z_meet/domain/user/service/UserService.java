package com.gdg.z_meet.domain.user.service;

import com.gdg.z_meet.domain.chat.service.ChatRoomCommandService;
import com.gdg.z_meet.domain.fcm.repository.FcmTokenRepository;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.entity.enums.Level;
import com.gdg.z_meet.domain.user.repository.RefreshTokenRepository;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.jwt.JwtUtil;
import com.gdg.z_meet.domain.user.dto.Token;
import com.gdg.z_meet.domain.user.dto.UserReq;
import com.gdg.z_meet.domain.user.dto.UserRes;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.response.Code;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseCookie;
import org.springframework.transaction.annotation.Transactional;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;

import java.util.Optional;

@Slf4j
@Service
@RequiredArgsConstructor
public class UserService {
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final RefreshTokenRepository refreshTokenRepository;
    private final JwtUtil jwtUtil;
    private final BCryptPasswordEncoder encoder;
    private final ChatRoomCommandService chatRoomCommandService;
    private final FcmTokenRepository fcmTokenRepository;

    @Transactional
    public UserRes.SignUpRes signup(UserReq.SignUpReq signUpReq) {
        String password = signUpReq.getPassword();
        if (password == null || !password.matches("\\d{4,6}")
                || password.length() < 4 || password.length() > 6) {
            throw new BusinessException(Code.INVALID_PASSWORD);
        }
        if (isStudentNumberDuplicate(signUpReq.getStudentNumber())) {
            throw new BusinessException(Code.DUPLICATE_STUDENT_NUMBER);
        }
        if (isPhoneNumberDuplicate(signUpReq.getPhoneNumber())) {
            throw new BusinessException(Code.DUPLICATE_PHONE_NUMBER);
        }
        if (isNicknameDuplicate(signUpReq.getNickname())) {
            throw new BusinessException(Code.DUPLICATE_NICKNAME);
        }

        String encodedPassword = encoder.encode(signUpReq.getPassword());

        User user = User.builder()
                .studentNumber(signUpReq.getStudentNumber())
                .password(encodedPassword)
                .name(signUpReq.getName())
                .phoneNumber(signUpReq.getPhoneNumber())
                .build();
        User newUser = userRepository.save(user);

        UserProfile userProfile = UserProfile.builder()
                .nickname(signUpReq.getNickname())
                .emoji(signUpReq.getEmoji())
                .music(signUpReq.getMusic())
                .mbti(signUpReq.getMbti())
                .style(signUpReq.getStyle())
                .idealType(signUpReq.getIdealType())
                .idealAge(signUpReq.getIdealAge())
                .gender(signUpReq.getGender())
                .grade(signUpReq.getGrade())
                .major(signUpReq.getMajor())
                .age(signUpReq.getAge())
                .level(Level.LIGHT)
                .user(user)
                .build();
        userProfileRepository.save(userProfile);

        return UserRes.SignUpRes.builder().message("회원가입 성공!").build();
    }

    public Token login(UserReq.LoginReq loginReq, HttpServletResponse response) {
        User user = userRepository.findByStudentNumber(loginReq.getStudentNumber())
                .orElseThrow(() -> new BusinessException(Code.PROFILE_NOT_FOUND));

        if (!encoder.matches(loginReq.getPassword(), user.getPassword())) {
            throw new BusinessException(Code.INVALID_PASSWORD);
        }

        if (user.isDeleted()) {
            throw new BusinessException(Code.USER_DELETED);
        }

        Token token = jwtUtil.createToken(loginReq.getStudentNumber(), user.getId());
        jwtUtil.createCookie(response, token.getRefreshToken());

        refreshTokenRepository.save(token.getRefreshToken(), user.getId(), jwtUtil.getRefreshTokenValidTime() / 1000);
        return token;
    }

    public void logout(HttpServletRequest request, HttpServletResponse response, String fcmToken) {
        String refreshToken = jwtUtil.getRefreshTokenFromCookie(request);
        if (refreshToken != null) {
            refreshTokenRepository.delete(refreshToken);
        }
        clearRefreshTokenCookie(response);

        // 해당 디바이스의 FCM 토큰만 삭제됨
        if (fcmToken != null && !fcmToken.isBlank()) {
            fcmTokenRepository.deleteByToken(fcmToken);
        }
    }

    @Transactional(readOnly = true)
    public UserRes.ProfileRes getProfile(Long userId) {
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(Code.PROFILE_NOT_FOUND));

        User user = userProfile.getUser();

        return UserRes.ProfileRes.builder()
                .id(user.getId())
                .name(user.getName())
                .studentNumber(user.getStudentNumber())
                .nickname(userProfile.getNickname())
                .phoneNumber(user.getPhoneNumber())
                .gender(userProfile.getGender())
                .emoji(userProfile.getEmoji())
                .mbti(userProfile.getMbti())
                .style(userProfile.getStyle())
                .idealType(userProfile.getIdealType())
                .idealAge(userProfile.getIdealAge())
                .grade(userProfile.getGrade())
                .major(userProfile.getMajor())
                .age(userProfile.getAge())
                .music(userProfile.getMusic())
                .level(userProfile.getLevel())
                .profileStatus(userProfile.getProfileStatus())
                .build();
    }

    @Transactional(readOnly = true)
    public UserRes.UserProfileRes getUserProfile(Long userId, String nickname) {

        User user = userRepository.findByIdWithProfile(userId);

        UserProfile userProfile = userProfileRepository.findByNickname(nickname)
                .orElseThrow(() -> new BusinessException(Code.PROFILE_NOT_FOUND));

        User findUser = userProfile.getUser();

        return UserRes.UserProfileRes.builder()
                .nickname(userProfile.getNickname())
                .studentNumber(findUser.getStudentNumber().substring(2,4))
                .gender(userProfile.getGender())
                .emoji(userProfile.getEmoji())
                .mbti(userProfile.getMbti())
                .style(userProfile.getStyle())
                .idealType(userProfile.getIdealType())
                .idealAge(userProfile.getIdealAge())
                .major(userProfile.getMajor().getShortName())
                .age(userProfile.getAge())
                .music(userProfile.getMusic())
                .level(String.valueOf(user.getUserProfile().getLevel()))
                .build();
    }

    @CacheEvict(value = "userDetails", key = "#studentNumber")
    @Transactional
    public UserRes.NicknameUpdateRes updateNickname(Long userId, UserReq.NicknameUpdateReq request) {
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(Code.PROFILE_NOT_FOUND));

        userProfile.setNickname(request.getNickname());
        userProfileRepository.save(userProfile);

        return UserRes.NicknameUpdateRes.builder()
                .nickname(userProfile.getNickname())
                .build();
    }

    @CacheEvict(value = "userDetails", key = "#studentNumber")
    @Transactional
    public UserRes.EmojiUpdateRes updateEmoji(Long userId, UserReq.EmojiUpdateReq request) {
        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(Code.PROFILE_NOT_FOUND));

        userProfile.setEmoji(request.getEmoji());
        userProfileRepository.save(userProfile);

        return UserRes.EmojiUpdateRes.builder()
                .emoji(userProfile.getEmoji())
                .build();
    }

    @Transactional
    public void withdraw(Long userId, HttpServletRequest request, HttpServletResponse response) {
        User user = userRepository.findById(userId)
                .orElseThrow(() -> new BusinessException(Code.PROFILE_NOT_FOUND));

        chatRoomCommandService.removeUser(userId);

        fcmTokenRepository.deleteAllByUser(user);

        user.setIsDeleted(true);
        userRepository.save(user);

//        itemPurchaseRepository.deleteByBuyerId(userId);
        userProfileRepository.deleteByUserId(userId);

        String refreshToken = jwtUtil.getRefreshTokenFromCookie(request);
        refreshTokenRepository.delete(refreshToken);

        clearRefreshTokenCookie(response);
    }

    public boolean isStudentNumberDuplicate(String studentNumber) {
        return userRepository.existsByStudentNumber(studentNumber);
    }

    public boolean isPhoneNumberDuplicate(String phoneNumber) {
        return userRepository.existsByPhoneNumber(phoneNumber);
    }

    public boolean isNicknameDuplicate(String nickname) {
        return userProfileRepository.existsByNickname(nickname);
    }

    private void clearRefreshTokenCookie(HttpServletResponse response) {
        ResponseCookie cookie = ResponseCookie.from("refreshToken", null)
                .httpOnly(true)
                .secure(true)
                .path("/")
                .maxAge(0)
                .sameSite("None")
                .build();
        response.addHeader(HttpHeaders.SET_COOKIE, cookie.toString());
        log.info("Refresh token cookie cleared");
    }

    @Transactional
    public UserRes.UpdatePasswordRes resetPassword(String name, String studentNumber, String phoneNumber, String newPassword, String confirmPassword) {
        Optional<User> userOpt = userRepository.findByNameAndStudentNumberAndPhoneNumber(name, studentNumber, phoneNumber);
        if (userOpt.isEmpty()){
            throw new BusinessException(Code.PROFILE_NOT_FOUND);
        }
        if (!newPassword.equals(confirmPassword)) {
            throw new BusinessException(Code.PASSWORD_MISMATCH);
        }

        User user = userOpt.get();
        user.setPassword(encoder.encode(newPassword));

        return UserRes.UpdatePasswordRes.builder()
                .message("비밀번호가 재설정되었습니다.")
                .build();
    }

    @Transactional
    public UserRes.IncreaseRes increaseHiTicket(String nickname) {
        UserProfile userProfile = userProfileRepository.findByNickname(nickname)
                .orElseThrow(() -> new BusinessException(Code.PROFILE_NOT_FOUND));

        userProfile.setTicket(99);
        userProfile.setHi(99);

        return UserRes.IncreaseRes.builder()
                .message(String.format("%s님의 하이와 티켓 수가 증가되었습니다.", nickname))
                .build();
    }
}