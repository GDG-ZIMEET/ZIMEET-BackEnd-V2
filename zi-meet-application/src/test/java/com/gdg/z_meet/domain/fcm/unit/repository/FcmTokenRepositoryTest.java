package com.gdg.z_meet.domain.fcm.unit.repository;

import com.gdg.z_meet.domain.fcm.entity.FcmToken;
import com.gdg.z_meet.domain.user.entity.User;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class FcmTokenRepositoryTest {

    @Test
    void FCM토큰_엔티티_생성_테스트() {
        // given
        User user = User.builder()
                .studentNumber("12345678")
                .name("테스트사용자")
                .phoneNumber("010-1234-5678")
                .password("password")
                .pushAgree(true)
                .build();

        // when
        FcmToken token = FcmToken.builder()
                .user(user)
                .token("test-fcm-token")
                .build();

        // then
        assertThat(token.getToken()).isEqualTo("test-fcm-token");
        assertThat(token.getUser()).isEqualTo(user);
        assertThat(token.getUser().isPushAgree()).isTrue();
    }

    @Test
    void FCM토큰_양방향_관계_설정_테스트() {
        // given
        User user = User.builder()
                .studentNumber("11111111")
                .name("사용자1")
                .phoneNumber("010-1111-1111")
                .password("password")
                .pushAgree(true)
                .build();

        FcmToken token = FcmToken.builder()
                .user(user)
                .token("token1")
                .build();

        // when
        token.setUser(user);

        // then
        assertThat(token.getUser()).isEqualTo(user);
        assertThat(user.getFcmToken()).isEqualTo(token);
    }

    @Test
    void FCM토큰_업데이트_테스트() {
        // given
        User user = User.builder()
                .studentNumber("22222222")
                .name("사용자2")
                .phoneNumber("010-2222-2222")
                .password("password")
                .pushAgree(false)
                .build();

        FcmToken token = FcmToken.builder()
                .user(user)
                .token("old-token")
                .build();

        // when
        token.updateToken("new-token");

        // then
        assertThat(token.getToken()).isEqualTo("new-token");
    }
}
