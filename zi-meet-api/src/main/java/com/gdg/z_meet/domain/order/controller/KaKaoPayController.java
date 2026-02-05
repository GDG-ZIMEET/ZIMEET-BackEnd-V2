package com.gdg.z_meet.domain.order.controller;

import com.gdg.z_meet.domain.order.converter.KaKaoPayApproveConverter;
import com.gdg.z_meet.domain.order.converter.KaKaoPayCancelConverter;
import com.gdg.z_meet.domain.order.converter.KaKaoPayReadyConverter;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.dto.KaKaoPayCancelDTO;
import com.gdg.z_meet.domain.order.dto.KaKaoPayReadyDTO;
import com.gdg.z_meet.domain.order.service.cancel.KakaoPayCancelService;
import com.gdg.z_meet.domain.order.service.ready.KakaoPayReadyService;
import com.gdg.z_meet.domain.order.service.approve.KakaoPayApproveService;
import com.gdg.z_meet.global.response.Response;
import com.gdg.z_meet.global.security.annotation.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
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
    private final KakaoPayCancelService kaKaoPayCancelService;

    private final com.gdg.z_meet.domain.order.service.sync.PaymentSyncService paymentSyncService;

    @Operation(summary = "결제 준비 API", description = "주문 정보를 받아 사용자가 결제 화면으로 이동하는 '결제 준비'의 단계입니다.")
    @PostMapping("/ready")
    public Response<KaKaoPayReadyDTO.Response> ready(
            @AuthUser Long userId,
            @Valid @RequestBody KaKaoPayReadyDTO.Request request,
            @Parameter(description = "멱등성 키", example = "550e8400-e29b-41d4-a716-446655440000") @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        KaKaoPayReadyDTO.Parameter parameter = KaKaoPayReadyConverter.toParameter(userId, request);
        KaKaoPayReadyDTO.Response response = kaKaoPayReadyService.ready(parameter, idempotencyKey);
        return Response.ok(response);
    }

    @Operation(summary = "결제 승인 API", description = "카카오페이 결제 승인 요청을 처리합니다. 중복 처리를 방지하기 위해 멱등성 키를 권장합니다.")
    @PostMapping("/approve")
    public Response<KaKaoPayApproveDTO.Response> approve(
            @AuthUser Long userId,
            @Valid @RequestBody KaKaoPayApproveDTO.Request request,
            @Parameter(description = "멱등성 키", example = "550e8400-e29b-41d4-a716-446655440000") @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        KaKaoPayApproveDTO.Parameter parameter = KaKaoPayApproveConverter.toParameter(userId, request);
        KaKaoPayApproveDTO.Response response = kaKaoPayApproveService.approve(parameter, idempotencyKey);
        return Response.ok(response);
    }

    @Operation(summary = "결제 취소 API", description = "카카오페이 결제 취소 요청을 처리합니다.")
    @PostMapping("/cancel")
    public Response<KaKaoPayCancelDTO.Response> cancel(
            @AuthUser Long userId,
            @Valid @RequestBody KaKaoPayCancelDTO.Request request,
            @Parameter(description = "멱등성 키", example = "550e8400-e29b-41d4-a716-446655440000") @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        KaKaoPayCancelDTO.Parameter parameter = KaKaoPayCancelConverter.toParameter(userId, request);
        KaKaoPayCancelDTO.Response response = kaKaoPayCancelService.cancel(parameter, userId);
        return Response.ok(response);
    }

    @Operation(summary = "결제 결과 Webhook (통지)", description = "카카오페이 서버에서 결제 결과를 전송하는 웹훅 엔드포인트입니다.")
    @PostMapping("/callback")
    public String callback(@RequestBody com.gdg.z_meet.domain.order.dto.KakaoPayWebhookDTO webhookData) {
        log.info("카카오페이 Webhook 수신 - orderId: {}, status: {}",
                webhookData.getPartner_order_id(), webhookData.getStatus());

        try {
            paymentSyncService.syncPaymentStatus(webhookData.getPartner_order_id());
        } catch (Exception e) {
            log.error("Webhook 처리 중 에러 발생 - orderId: {}", webhookData.getPartner_order_id(), e);
        }

        // 카카오페이 가이드에 따라 성공 응답 반환 (보통 빈 문자열 혹은 OK)
        return "OK";
    }
}