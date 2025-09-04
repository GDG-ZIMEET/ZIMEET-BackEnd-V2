package com.gdg.z_meet.domain.order.converter;

import com.gdg.z_meet.domain.order.dto.KaKaoPayReadyDTO;
import com.gdg.z_meet.domain.order.entity.KaKaoPayData;
import com.gdg.z_meet.domain.order.entity.ProductType;
import com.gdg.z_meet.domain.user.entity.User;

public class KaKaoPayReadyConverter {

    // JSON 데이터 내부 로직에 맞게 가공
    public static KaKaoPayReadyDTO.Parameter toParameter(Long userId, KaKaoPayReadyDTO.Request request) {

        return KaKaoPayReadyDTO.Parameter.builder()
                .buyerId(userId)               // userId -> buyerId
                .teamId(request.getTeamId())
                .productType(request.getProductType())
                .totalPrice(request.getTotalPrice())
                .vat(request.getTotalPrice()/10)     // 과세 10%
                .build();
    }


    // 결제 정보 변환
    public static KaKaoPayData toKakaoPayData(KaKaoPayReadyDTO.KakaoApiResponse kakaoApiResponse,
                                              KaKaoPayReadyDTO.Parameter parameter,  String orderId, User buyer) {

        // productType, totalPrice 추가
        return KaKaoPayData.builder()
                .orderId(orderId)
                .tid(kakaoApiResponse.getTid())
                .productType(ProductType.valueOf(parameter.getProductType()))
                .totalPrice(parameter.getTotalPrice())
                .buyer(buyer)
                .build();
    }


    public static KaKaoPayReadyDTO.Response toResponse(KaKaoPayReadyDTO.KakaoApiResponse kakaoApiResponse, String orderId) {

        return KaKaoPayReadyDTO.Response.builder()
                .nextRedirectPcUrl(kakaoApiResponse.getNext_redirect_pc_url())
                .orderId(orderId)
                .build();
    }

}
