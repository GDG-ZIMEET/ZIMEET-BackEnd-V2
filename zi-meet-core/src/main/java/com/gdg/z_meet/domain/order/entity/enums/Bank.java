package com.gdg.z_meet.domain.order.entity.enums;

import lombok.AllArgsConstructor;
import lombok.Getter;

@Getter
@AllArgsConstructor
public enum Bank {
    NH("농협은행"),
    IBK("기업은행"),
    MG("새마을금고"),
    KB("국민은행"),
    SC("SC제일은행"),
    CU("신협"),
    KAKAO("카카오뱅크"),
    SHINHAN("신한은행"),
    DGB("대구은행"),
    TOSS("토스뱅크"),
    HANA("하나은행"),
    BUSAN("부산은행"),
    WOORI("우리은행"),
    GWANGJU("광주은행"),
    JEONBUK("전북은행"),
    POST("우체국"),
    SUHYUP("수협은행"),
    KDB("산업은행"),
    CITI("씨티은행"),
    K_BANK("케이뱅크");

    private final String description;
}
