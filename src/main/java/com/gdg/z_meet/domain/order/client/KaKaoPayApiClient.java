package com.gdg.z_meet.domain.order.client;

import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.dto.KaKaoPayCancelDTO;
import com.gdg.z_meet.domain.order.dto.KaKaoPayReadyDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.user.entity.User;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpMethod;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClientException;
import org.springframework.web.client.RestTemplate;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class KaKaoPayApiClient {

    private final RestTemplate restTemplate;

    @Value("${kakao.pay.ready-url}")
    private String READY_URL;
    
    @Value("${kakao.pay.approve-url}")
    private String APPROVE_URL;
    
    @Value("${kakao.pay.cancel-api-url:https://open-api.kakaopay.com/online/v1/payment/cancel}")
    private String CANCEL_API_URL;
    
    @Value("${kakao.pay.inquiry-api-url:https://open-api.kakaopay.com/online/v1/payment/order}")
    private String INQUIRY_API_URL;
    
    @Value("${kakao.pay.cid}")
    private String cid;
    
    @Value("${kakao.pay.secret-key}")
    private String secretKey;
    
    @Value("${kakao.pay.approval-url}")
    private String approvalUrl;
    
    @Value("${kakao.pay.cancel-url}")
    private String cancelUrl;
    
    @Value("${kakao.pay.fail-url}")
    private String failUrl;

    // 카카오 페이 결제 준비 API
    public Optional<KaKaoPayReadyDTO.KakaoApiResponse> requestPaymentReady(
            KaKaoPayReadyDTO.Parameter parameter, String orderId, User buyer) {

        try {
            Map<String, String> parameters = getReadyParams(parameter, orderId, buyer);
            
            HttpHeaders headers = getHeaders();
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(parameters, headers);

            ResponseEntity<KaKaoPayReadyDTO.KakaoApiResponse> response = restTemplate.postForEntity(
                    READY_URL, requestEntity, KaKaoPayReadyDTO.KakaoApiResponse.class
            );

            return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            log.error("카카오페이 결제 준비 API 호출 실패: {}", e.getMessage());
            return Optional.empty();
        }
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
                approvalUrl + "?productType=" + parameter.getProductType() + "&orderId=" + orderId);
        params.put("cancel_url",
                cancelUrl + "?orderId=" + orderId);
        params.put("fail_url",
                failUrl + "?orderId=" + orderId);

        return params;
    }


    // 카카오페이 결제 승인 API
    // parameter : userId, pgToken, orderId
    // kakaoPayData : orderId, tid, buyer
    public Optional<KaKaoPayApproveDTO.KaKaoApiResponse> requestPaymentApprove(
            KaKaoPayApproveDTO.Parameter parameter, KakaoPayData kakaoPayData) {

        try {
            Map<String, String> parameters = getApproveParams(parameter, kakaoPayData);
            
            HttpHeaders headers = getHeaders();
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(parameters, headers);

            ResponseEntity<KaKaoPayApproveDTO.KaKaoApiResponse> response = restTemplate.postForEntity(
                    APPROVE_URL, requestEntity, KaKaoPayApproveDTO.KaKaoApiResponse.class
            );

            log.info("KaKaoPay API Response: {}", response.getBody());
            
            return Optional.ofNullable(response.getBody());
        } catch (ResourceAccessException e) {
            // 타임아웃 예외 처리
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException) {
                log.warn("카카오페이 결제 승인 API 호출 타임아웃 발생 - orderId: {}, tid: {}", 
                        parameter.getOrderId(), kakaoPayData.getTid());
                
                // Read Timeout인 경우 결제 상태 조회 시도
                if (kakaoPayData.getTid() != null && !kakaoPayData.getTid().isEmpty()) {
                    Optional<KaKaoPayApproveDTO.KaKaoApiResponse> inquiryResult = 
                            inquirePaymentStatus(kakaoPayData.getTid());
                    if (inquiryResult.isPresent()) {
                        log.info("타임아웃 발생 후 결제 상태 조회 성공 - orderId: {}, tid: {}", 
                                parameter.getOrderId(), kakaoPayData.getTid());
                        return inquiryResult;
                    }
                }
            } else {
                log.error("카카오페이 결제 승인 API 연결 실패 (Connection Timeout 가능) - orderId: {}, error: {}", 
                        parameter.getOrderId(), e.getMessage());
            }
            return Optional.empty();
        } catch (RestClientException e) {
            log.error("카카오페이 결제 승인 API 호출 실패 - orderId: {}, error: {}", 
                    parameter.getOrderId(), e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("카카오페이 결제 승인 API 호출 중 예상치 못한 오류 - orderId: {}, error: {}", 
                    parameter.getOrderId(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    // 결제 승인 파라미터 생성 (카카오 페이 DB tid 기반)
    private Map<String, String> getApproveParams(KaKaoPayApproveDTO.Parameter parameter, KakaoPayData kakaoPayData) {

        Map<String, String> params = new HashMap<>();

        params.put("cid", cid);                      // 가맹점 코드
        params.put("tid", kakaoPayData.getTid());    // 결제 고유번호
        params.put("partner_order_id", parameter.getOrderId());    // 내부 주문 ID
        params.put("partner_user_id", String.valueOf(kakaoPayData.getBuyer().getId()));    // 결제한 사용자 id
        params.put("pg_token", parameter.getPgToken());

        return params;
    }

    // 카카오페이 결제 취소 API
    public Optional<KaKaoPayCancelDTO.KakaoApiResponse> requestPaymentCancel(
            KaKaoPayCancelDTO.Parameter parameter) {

        try {
            Map<String, String> parameters = getCancelParams(parameter);
            
            HttpHeaders headers = getHeaders();
            HttpEntity<Map<String, String>> requestEntity = new HttpEntity<>(parameters, headers);

            ResponseEntity<KaKaoPayCancelDTO.KakaoApiResponse> response = restTemplate.postForEntity(
                    CANCEL_API_URL, requestEntity, KaKaoPayCancelDTO.KakaoApiResponse.class
            );

            log.info("KaKaoPay 취소 API Response: {}", response.getBody());
            
            return Optional.ofNullable(response.getBody());
        } catch (ResourceAccessException e) {
            // 타임아웃 예외 처리
            Throwable cause = e.getCause();
            if (cause instanceof SocketTimeoutException) {
                log.warn("카카오페이 결제 취소 API 호출 타임아웃 발생 - tid: {}", parameter.getTid());
                
                // Read Timeout인 경우 취소 상태 조회 시도
                if (parameter.getTid() != null && !parameter.getTid().isEmpty()) {
                    Optional<KaKaoPayApproveDTO.KaKaoApiResponse> inquiryResult = 
                            inquirePaymentStatus(parameter.getTid());
                    if (inquiryResult.isPresent()) {
                        // 조회 결과에서 취소 상태 확인
                        // 카카오페이 응답에서 취소 여부를 확인할 수 있다면 처리
                        log.info("타임아웃 발생 후 결제 상태 조회 성공 - tid: {}", parameter.getTid());
                        // 취소 상태 조회는 성공했지만, 취소 API 응답이 아니므로 Optional.empty() 반환
                        // 실제 취소 성공 여부는 조회 결과를 분석해야 함
                    }
                }
            } else {
                log.error("카카오페이 결제 취소 API 연결 실패 (Connection Timeout 가능) - tid: {}, error: {}", 
                        parameter.getTid(), e.getMessage());
            }
            return Optional.empty();
        } catch (RestClientException e) {
            log.error("카카오페이 결제 취소 API 호출 실패 - tid: {}, error: {}", 
                    parameter.getTid(), e.getMessage());
            return Optional.empty();
        } catch (Exception e) {
            log.error("카카오페이 결제 취소 API 호출 중 예상치 못한 오류 - tid: {}, error: {}", 
                    parameter.getTid(), e.getMessage(), e);
            return Optional.empty();
        }
    }

    // 결제 취소 파라미터 생성
    private Map<String, String> getCancelParams(KaKaoPayCancelDTO.Parameter parameter) {
        Map<String, String> params = new HashMap<>();

        params.put("cid", cid);                      // 가맹점 코드
        params.put("tid", parameter.getTid());      // 결제 고유번호
        params.put("cancel_amount", String.valueOf(parameter.getCancelAmount()));  // 취소 금액
        params.put("cancel_tax_free_amount", String.valueOf(parameter.getCancelTaxFreeAmount() != null ? parameter.getCancelTaxFreeAmount() : 0));  // 취소 비과세 금액
        
        if (parameter.getCancelReason() != null && !parameter.getCancelReason().isEmpty()) {
            params.put("cancel_reason", parameter.getCancelReason());
        }

        return params;
    }

    /**
     * 카카오페이 결제 상태 조회 API
     * 타임아웃 발생 시 실제 결제 성공 여부를 확인하기 위해 사용
     * 
     * @param tid 결제 고유번호
     * @return 결제 정보 (성공 시)
     */
    public Optional<KaKaoPayApproveDTO.KaKaoApiResponse> inquirePaymentStatus(String tid) {
        try {
            String url = INQUIRY_API_URL + "?cid=" + cid + "&tid=" + tid;
            
            HttpHeaders headers = getHeaders();
            HttpEntity<Void> requestEntity = new HttpEntity<>(headers);

            ResponseEntity<KaKaoPayApproveDTO.KaKaoApiResponse> response = restTemplate.exchange(
                    url, HttpMethod.GET, requestEntity, KaKaoPayApproveDTO.KaKaoApiResponse.class
            );

            log.info("카카오페이 결제 상태 조회 성공 - tid: {}, status: {}", 
                    tid, response.getBody() != null ? "SUCCESS" : "EMPTY");
            
            return Optional.ofNullable(response.getBody());
        } catch (Exception e) {
            log.warn("카카오페이 결제 상태 조회 실패 - tid: {}, error: {}", tid, e.getMessage());
            return Optional.empty();
        }
    }

    private HttpHeaders getHeaders() {
        HttpHeaders headers = new HttpHeaders();
        headers.set("Authorization", "SECRET_KEY " + secretKey);
        headers.set("Content-type", "application/json");
        return headers;
    }
}
