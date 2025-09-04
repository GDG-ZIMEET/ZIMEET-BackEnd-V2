//package com.gdg.z_meet.domain.fcm.feign;
//
//import org.springframework.cloud.openfeign.FeignClient;
//import org.springframework.stereotype.Component;
//import org.springframework.web.bind.annotation.PostMapping;
//import org.springframework.web.bind.annotation.RequestBody;
//import org.springframework.web.bind.annotation.RequestHeader;
//
//// FCM 토큰 조회 후, Firebase 서버에 알림 전송
//@FeignClient(name = "FCMFeign", url = "https://fcm.googleapis.com", configuration = FcmFeignConfig.class)
//@Component
//public interface FcmFeignClient {
//
//    @PostMapping("/v1/projects/zi-meet/messages:send")
//    FcmFeignRes getFCMResponse(@RequestHeader("Authorization") String token, @RequestBody String fcmMessage);
//}