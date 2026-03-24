package com.gdg.z_meet.domain.order.converter;

import com.gdg.z_meet.domain.order.dto.KaKaoPayCancelDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;

public class KaKaoPayCancelConverter {

    public static KaKaoPayCancelDTO.Parameter toParameter(Long userId, KaKaoPayCancelDTO.Request request) {
        return KaKaoPayCancelDTO.Parameter.builder()
                .orderId(request.getOrderId())
                .cancelReason(request.getCancelReason())
                .build();
    }

    public static KaKaoPayCancelDTO.Parameter toParameter(KakaoPayData kakaoPayData, String cancelReason) {
        return KaKaoPayCancelDTO.Parameter.builder()
                .orderId(kakaoPayData.getOrderId())
                .tid(kakaoPayData.getTid())
                .cancelAmount(kakaoPayData.getTotalPrice())
                .cancelTaxFreeAmount(0L)
                .cancelReason(cancelReason != null ? cancelReason : "내부 처리 실패로 인한 자동 취소")
                .build();
    }

    public static KaKaoPayCancelDTO.Response toResponse(KaKaoPayCancelDTO.KakaoApiResponse kakaoApiResponse, String orderId) {
        return KaKaoPayCancelDTO.Response.builder()
                .orderId(orderId)
                .canceledAt(kakaoApiResponse.getCanceled_at())
                .status(kakaoApiResponse.getStatus())
                .build();
    }
}

