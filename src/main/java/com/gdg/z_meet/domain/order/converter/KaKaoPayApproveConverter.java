package com.gdg.z_meet.domain.order.converter;


import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.ItemPurchase;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;


public class KaKaoPayApproveConverter {

    public static KaKaoPayApproveDTO.Parameter toParameter(Long userId, KaKaoPayApproveDTO.Request request) {

        return KaKaoPayApproveDTO.Parameter.builder()
                .userId(userId)
                .orderId(request.getOrderId())
                .pgToken(request.getPgToken())
                .build();
    }

    public static ItemPurchase toItemPurchase(KaKaoPayApproveDTO.KaKaoApiResponse kakaoApiResponse, KakaoPayData kaKaoPayData, User buyer, Team team, UserProfile userProfile) {

        return ItemPurchase.builder()
                .orderId(kaKaoPayData.getOrderId())
                .tid(kakaoApiResponse.getTid())
                .productType(kaKaoPayData.getProductType())
                .totalPrice(kaKaoPayData.getTotalPrice())
                .vat(kakaoApiResponse.getAmount().getVat())
                .buyer(buyer)
                .team(team)
                .userProfile(userProfile)
                .build();
    }

    public static KaKaoPayApproveDTO.Response toResponse(KaKaoPayApproveDTO.KaKaoApiResponse kakaoApiResponse, String orderId) {

        return KaKaoPayApproveDTO.Response.builder()
                .orderId(orderId)
                .approvedAt(kakaoApiResponse.getApproved_at())
                .build();
    }
}