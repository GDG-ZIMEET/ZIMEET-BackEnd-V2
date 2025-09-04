//package com.gdg.z_meet.domain.fcm.feign;
//
//import com.gdg.z_meet.global.exception.FeignClientExceptionErrorDecoder;
//import feign.Logger;
//import feign.RequestInterceptor;
//import feign.codec.ErrorDecoder;
//import lombok.RequiredArgsConstructor;
//import org.springframework.context.annotation.Bean;
//
//@RequiredArgsConstructor
//public class FcmFeignConfig {
//
//    @Bean
//    public RequestInterceptor requestInterceptor(){
//        // 모든 요청에 Content-Type: application/json;charset=UTF-8(JSON 형식 + 한글 깨짐 방지)  자동 추가
//        return template -> template.header("Content-Type", "application/json;charset=UTF-8");
//    }
//
//    @Bean
//    public ErrorDecoder errorDecoder() {
//        return  new FeignClientExceptionErrorDecoder();
//    }
//
//    @Bean
//    Logger.Level feignLoggerLevel() {
//        return Logger.Level.FULL;
//    }
//}
