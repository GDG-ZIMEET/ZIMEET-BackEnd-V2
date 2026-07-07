package com.gdg.z_meet.domain.order.controller;

import com.gdg.z_meet.domain.order.dto.PaymentOperationAlertResponse;
import com.gdg.z_meet.domain.order.service.monitoring.PaymentOperationAlertService;
import com.gdg.z_meet.global.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.Map;

@RestController
@RequestMapping("/api/v1/admin/payment-operations")
@RequiredArgsConstructor
@Tag(name = "Admin Payment Operations", description = "결제 불확실 상태 및 보상 트랜잭션 운영 API")
public class AdminPaymentOperationController {

    private final PaymentOperationAlertService paymentOperationAlertService;

    @GetMapping("/alerts")
    @Operation(summary = "운영 확인이 필요한 결제 목록 조회")
    public Response<Page<PaymentOperationAlertResponse>> getPaymentAlerts(Pageable pageable) {
        return Response.ok(paymentOperationAlertService.findAttentionTargets(pageable));
    }

    @GetMapping("/alerts/count")
    @Operation(summary = "운영 확인이 필요한 결제 수 조회")
    public Response<Map<String, Long>> countPaymentAlerts() {
        return Response.ok(Map.of("count", paymentOperationAlertService.countAttentionTargets()));
    }
}
