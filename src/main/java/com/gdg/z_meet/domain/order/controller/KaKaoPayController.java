package com.gdg.z_meet.domain.order.controller;

import com.gdg.z_meet.domain.order.converter.KaKaoPayApproveConverter;
import com.gdg.z_meet.domain.order.converter.KaKaoPayReadyConverter;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.dto.KaKaoPayReadyDTO;
import com.gdg.z_meet.domain.order.service.KaKaoPayService;
import com.gdg.z_meet.global.common.AuthenticatedUserUtils;
import com.gdg.z_meet.global.response.Response;
import io.swagger.v3.oas.annotations.Operation;

import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;


@RestController
@RequestMapping("/api/kakao-pay")
@RequiredArgsConstructor
@Slf4j
@Tag(name = "KaKao Pay", description = "카카오페이 결제 API")
public class KaKaoPayController {

    private final KaKaoPayService kaKaoPayService;

    @Operation(summary = "결제 준비 API",  description = "주문 정보를 받아 사용자가 결제 화면으로 이동하는 '결제 준비'의 단계입니다.")
    @PostMapping("/ready")
    public Response<KaKaoPayReadyDTO.Response> KaKaoPayReady(@Valid @RequestBody KaKaoPayReadyDTO.Request request) {

        log.info("카카오페이 결제 준비 API 호출");
        Long userId = AuthenticatedUserUtils.getAuthenticatedUserId();
        KaKaoPayReadyDTO.Parameter parameter = KaKaoPayReadyConverter.toParameter(userId, request);
        KaKaoPayReadyDTO.Response response = kaKaoPayService.KaKaoPayReady(parameter);

        return Response.ok(response);
    }

    @Operation( summary = "결제 승인 API", description = "카카오페이 결제 승인 요청을 처리합니다.")
    @PostMapping("/approve")
    public Response<KaKaoPayApproveDTO.Response> KaKaoPayApprove(@Valid @RequestBody KaKaoPayApproveDTO.Request request) {

        log.info("카카오페이 결제 최종 승인 API 호출");
        Long userId = AuthenticatedUserUtils.getAuthenticatedUserId();
        KaKaoPayApproveDTO.Parameter parameter = KaKaoPayApproveConverter.toParameter(userId, request);
        KaKaoPayApproveDTO.Response response = kaKaoPayService.KaKaoPayApprove(parameter);

        return Response.ok(response);
    }
}
