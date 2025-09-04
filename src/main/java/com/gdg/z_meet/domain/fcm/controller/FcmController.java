package com.gdg.z_meet.domain.fcm.controller;

import com.gdg.z_meet.domain.fcm.dto.FcmBroadcastReq;
import com.gdg.z_meet.domain.fcm.dto.FcmTestReq;
import com.gdg.z_meet.domain.fcm.service.FcmService;
import com.gdg.z_meet.domain.user.dto.UserReq;
import com.gdg.z_meet.global.response.Response;
import com.gdg.z_meet.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.Parameters;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/api/fcm")
public class FcmController {

    private final FcmService fcmService;

    @Operation(summary = "푸시 알림 동의 API", description = "푸시 알림 동의 API 입니다.")
    @Parameters({
            @Parameter(name = "user", hidden = true)
    })
    @PostMapping("/push-agree")
    Response<String> pushAgree(@AuthUser Long userId, @RequestBody UserReq.pushAgreeReq req) {
        boolean pushAgree = fcmService.agreePush(userId, req);
        String responseMessage = pushAgree ? "푸시 알림 허용" : "푸시 알림 거부";
        return Response.ok(responseMessage);
    }


    @Operation(summary = "FCM 토큰 저장 API", description = "FCM 토큰 저장 API 입니다.")
    @Parameters({
            @Parameter(name = "user", hidden = true)
    })
    @PostMapping("/token")
    public Response<Long> syncFcmToken(@AuthUser Long userId, @RequestBody UserReq.saveFcmTokenReq req) {
        fcmService.syncFcmToken(userId, req);
        return Response.ok(userId);
    }

    @Operation(summary = "FCM 전체 공지용 메시지를 발송 API", description = "제목과 본문을 받아 모든 사용자에게 FCM 을 보냅니다.")
    @PostMapping("/broadcast")
    public Response<Void> broadcastMessage(@RequestBody FcmBroadcastReq req) {
        fcmService.broadcastToAllUsers(req.getTitle(), req.getBody());
        return Response.ok();
    }


    @Operation(summary = "FCM 테스트 API", description = "테스트용 API 입니다.")
    @PostMapping("/test")
    public Response<Void> testFCM(@AuthUser Long userId, @RequestBody FcmTestReq req) {
        fcmService.testFcmService(userId, req.getFcmToken());
        return Response.ok();
    }
}
