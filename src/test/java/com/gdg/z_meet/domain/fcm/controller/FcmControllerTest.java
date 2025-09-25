package com.gdg.z_meet.domain.fcm.controller;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdg.z_meet.domain.fcm.dto.FcmBroadcastRequest;
import com.gdg.z_meet.domain.fcm.dto.FcmTestRequest;
import com.gdg.z_meet.domain.fcm.service.core.FcmMessageService;
import com.gdg.z_meet.domain.fcm.service.token.FcmTokenService;
import com.gdg.z_meet.domain.user.dto.UserReq;
import com.gdg.z_meet.global.security.annotation.AuthUser;
import com.gdg.z_meet.global.security.jwt.JwtUtil;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.MethodParameter;
import org.springframework.http.MediaType;
import org.springframework.test.context.ContextConfiguration;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.web.bind.support.WebDataBinderFactory;
import org.springframework.web.context.request.NativeWebRequest;
import org.springframework.web.method.support.HandlerMethodArgumentResolver;
import org.springframework.web.method.support.ModelAndViewContainer;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

@WebMvcTest(controllers = FcmController.class)
@ContextConfiguration(classes = {FcmController.class, FcmControllerTest.TestConfig.class})
@AutoConfigureMockMvc(addFilters = false)
class FcmControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockBean
    private FcmTokenService fcmTokenService;

    @MockBean
    private FcmMessageService fcmMessageService;

    @MockBean
    private JwtUtil jwtUtil;

    @Configuration
    static class TestConfig implements WebMvcConfigurer {
        @Override
        public void addArgumentResolvers(List<HandlerMethodArgumentResolver> resolvers) {
            resolvers.add(new HandlerMethodArgumentResolver() {
                @Override
                public boolean supportsParameter(MethodParameter parameter) {
                    return parameter.getParameterAnnotation(AuthUser.class) != null
                            && parameter.getParameterType().equals(Long.class);
                }

                @Override
                public Object resolveArgument(MethodParameter parameter, ModelAndViewContainer mavContainer,
                                              NativeWebRequest webRequest, WebDataBinderFactory binderFactory) {
                    return 1L; // 테스트용 고정 userId
                }
            });
        }
    }

    @Test
    void 푸시알림_동의_성공_허용() throws Exception {
        UserReq.pushAgreeReq request = UserReq.pushAgreeReq.builder()
                .pushAgree(true)
                .build();

        when(fcmTokenService.agreePush(eq(1L), any(UserReq.pushAgreeReq.class))).thenReturn(true);

        mockMvc.perform(post("/api/fcm/push-agree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("푸시 알림 허용"));

        verify(fcmTokenService).agreePush(eq(1L), any(UserReq.pushAgreeReq.class));
    }

    @Test
    void 푸시알림_동의_성공_거부() throws Exception {
        UserReq.pushAgreeReq request = UserReq.pushAgreeReq.builder()
                .pushAgree(false)
                .build();

        when(fcmTokenService.agreePush(eq(1L), any(UserReq.pushAgreeReq.class))).thenReturn(false);

        mockMvc.perform(post("/api/fcm/push-agree")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value("푸시 알림 거부"));

        verify(fcmTokenService).agreePush(eq(1L), any(UserReq.pushAgreeReq.class));
    }

    @Test
    void FCM토큰_저장_성공() throws Exception {
        UserReq.saveFcmTokenReq request = UserReq.saveFcmTokenReq.builder()
                .fcmToken("test-fcm-token")
                .build();

        mockMvc.perform(post("/api/fcm/token")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data").value(1));

        verify(fcmTokenService).syncFcmToken(eq(1L), any(UserReq.saveFcmTokenReq.class));
    }

    @Test
    void 브로드캐스트_메시지_전송_성공() throws Exception {
        FcmBroadcastRequest request = new FcmBroadcastRequest("공지사항", "중요한 공지사항입니다.");

        mockMvc.perform(post("/api/fcm/broadcast")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(fcmMessageService).broadcastToAllUsers("공지사항", "중요한 공지사항입니다.");
    }

    @Test
    void FCM_테스트_메시지_전송_성공() throws Exception {
        FcmTestRequest request = new FcmTestRequest("test-fcm-token");

        mockMvc.perform(post("/api/fcm/test")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isOk());

        verify(fcmMessageService).testFcmService(1L, "test-fcm-token");
    }
}