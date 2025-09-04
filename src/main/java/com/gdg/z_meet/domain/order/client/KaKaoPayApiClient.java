package com.gdg.z_meet.domain.order.client;

import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.dto.KaKaoPayReadyDTO;
import com.gdg.z_meet.domain.order.entity.KaKaoPayData;
import com.gdg.z_meet.domain.user.entity.User;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestTemplate;

import java.util.HashMap;
import java.util.Map;

@Component
@Slf4j
public class KaKaoPayApiClient {

    private final RestTemplate restTemplate = new RestTemplate();

    @Value("${kakao.pay.ready-url}")
    private String READY_URL;
    @Value("${kakao.pay.approve-url}")
    private String APPROVE_URL;

    @Value("${kakao.pay.cid}")
    private String cid;
    @Value("${kakao.pay.secret-key}")
    private String secretKey;

    // 카카오 페이 결제 준비 API
    public KaKaoPayReadyDTO.KakaoApiResponse requestPaymentReady(
            KaKaoPayReadyDTO.Parameter parameter, String orderId, User buyer) {

        Map<String, String> parameters = getReadyParams(parameter, orderId, buyer);

        HttpHeaders headers = getHeaders();
        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(parameters, headers);

        ResponseEntity<KaKaoPayReadyDTO.KakaoApiResponse> response = restTemplate.postForEntity(
                READY_URL, requestEntity, KaKaoPayReadyDTO.KakaoApiResponse.class
        );

        // 카카오 서버 응답 반환 (tid)
        return response.getBody();
    }

    // 카카오 페이 서버에 보낼 요청 파라미터 구성
    private Map<String, String> getReadyParams(KaKaoPayReadyDTO.Parameter parameter, String orderId, User buyer) {

        Map<String, String> params = new HashMap<>();

        params.put("cid", cid);
        params.put("partner_order_id", orderId);
        params.put("partner_user_id", String.valueOf(buyer.getId()));
        params.put("item_name", parameter.getProductType());
        params.put("quantity", "1");                // 단건 결제
        params.put("total_amount", String.valueOf(parameter.getTotalPrice()));
        params.put("tax_free_amount", "0");         // 비과세 대상 없음
        params.put("vat_amount", String.valueOf(parameter.getVat()));     // 일반적으로 10%
        params.put("approval_url",
                "http://localhost:3000/purchase/approve?productType=" + parameter.getProductType() + "&orderId=" + orderId);
        params.put("cancel_url",
                "http://localhost:3000/purchase/cancel?orderId=" + orderId);
        params.put("fail_url",
                "http://localhost:3000/purchase/fail?orderId=" + orderId);

        return params;
    }


    // 카카오페이 결제 승인 API
    // parameter : userId, pgToken, orderId
    // kakaoPayData : orderId, tid, buyer
    public KaKaoPayApproveDTO.KaKaoApiResponse requestPaymentApprove(
            KaKaoPayApproveDTO.Parameter parameter, KaKaoPayData kakaoPayData) {

        Map<String, String> parameters = getApproveParams(parameter, kakaoPayData);

        HttpHeaders headers = getHeaders();
        HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(parameters, headers);

        ResponseEntity<KaKaoPayApproveDTO.KaKaoApiResponse> response = restTemplate.postForEntity(
                APPROVE_URL, requestEntity, KaKaoPayApproveDTO.KaKaoApiResponse.class
        );

        log.info("KaKaoPay API Response: {}", response.getBody());

        return response.getBody();
    }

    // 결제 승인 파라미터 생성 (카카오 페이 DB tid 기반)
    private Map<String, String> getApproveParams(KaKaoPayApproveDTO.Parameter parameter, KaKaoPayData kakaoPayData) {

        Map<String, String> params = new HashMap<>();

        params.put("cid", cid);                      // 가맹점 코드
        params.put("tid", kakaoPayData.getTid());    // 결제 고유번호
        params.put("partner_order_id", parameter.getOrderId());    // 내부 주문 ID
        params.put("partner_user_id", String.valueOf(kakaoPayData.getBuyer().getId()));    // 결제한 사용자 id
        params.put("pg_token", parameter.getPgToken());

        return params;
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "SECRET_KEY " + secretKey);
        headers.set("Content-type", "application/json");
        return headers;
    }
}
