package com.gdg.z_meet.domain.order.controller;

import com.gdg.z_meet.domain.order.converter.KaKaoPayApproveConverter;
import com.gdg.z_meet.domain.order.converter.KaKaoPayReadyConverter;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.dto.KaKaoPayReadyDTO;
import com.gdg.z_meet.domain.order.service.KakaoPayReadyService;
import com.gdg.z_meet.domain.order.service.KakaoPayApproveService;
import com.gdg.z_meet.global.response.Response;
import com.gdg.z_meet.global.security.annotation.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/kakao-pay")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "KaKaoPay", description = "카카오페이 결제 API")
public class KaKaoPayController {

    private final KakaoPayReadyService kaKaoPayReadyService;
    private final KakaoPayApproveService kaKaoPayApproveService;

    @Operation(summary = "결제 준비 API", description = "주문 정보를 받아 사용자가 결제 화면으로 이동하는 '결제 준비'의 단계입니다.")
    @PostMapping("/ready")
    public Response<KaKaoPayReadyDTO.Response> ready(
            @AuthUser Long userId, 
            @Valid @RequestBody KaKaoPayReadyDTO.Request request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        KaKaoPayReadyDTO.Parameter parameter = KaKaoPayReadyConverter.toParameter(userId, request);
        KaKaoPayReadyDTO.Response response = kaKaoPayReadyService.ready(parameter, idempotencyKey);
        return Response.ok(response);
    }

    @Operation(summary = "결제 승인 API", description = "카카오페이 결제 승인 요청을 처리합니다. 중복 처리를 방지하기 위해 멱등성 키를 권장합니다.")
    @PostMapping("/approve")
    public Response<KaKaoPayApproveDTO.Response> approve(
            @AuthUser Long userId, 
            @Valid @RequestBody KaKaoPayApproveDTO.Request request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {
        
        KaKaoPayApproveDTO.Parameter parameter = KaKaoPayApproveConverter.toParameter(userId, request);
        KaKaoPayApproveDTO.Response response = kaKaoPayApproveService.approve(parameter, idempotencyKey);
        return Response.ok(response);
    }
}