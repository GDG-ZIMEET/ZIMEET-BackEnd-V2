package com.gdg.z_meet.domain.order.client;

import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.dto.KaKaoPayCancelDTO;
import com.gdg.z_meet.domain.order.dto.KaKaoPayReadyDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;

import java.net.SocketTimeoutException;
import java.util.HashMap;
import java.util.Map;
import java.util.Optional;

@Component
@Slf4j
@RequiredArgsConstructor
public class KaKaoPayApiClient {

    private final RestClient restClient;

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

    @Value("${test.payment.delay-ms:0}")
    private long paymentDelayMs;

    // 카카오 페이 결제 준비 API
    public Optional<KaKaoPayReadyDTO.KakaoApiResponse> requestPaymentReady(
            KaKaoPayReadyDTO.Parameter parameter, String orderId, User buyer) {

        try {
            return Optional.ofNullable(restClient.post()
                    .uri(READY_URL)
                    .headers(h -> h.addAll(getHeaders()))
                    .body(getReadyParams(parameter, orderId, buyer))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.error("카카오페이 결제 준비 API 실패 - Status: {}, Body: {}",
                                res.getStatusCode(), res.getStatusText());
                        throw new BusinessException(Code.KAKAO_API_RESPONSE_ERROR);
                    })
                    .body(KaKaoPayReadyDTO.KakaoApiResponse.class));
        } catch (Exception e) {
            log.error("카카오페이 결제 준비 API 호출 중 오류 발생: {}", e.getMessage());
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
        params.put("quantity", "1");
        params.put("total_amount", String.valueOf(parameter.getTotalPrice()));
        params.put("tax_free_amount", "0");
        params.put("vat_amount", String.valueOf(parameter.getVat()));
        params.put("approval_url", approvalUrl + "?productType=" + parameter.getProductType() + "&orderId=" + orderId);
        params.put("cancel_url", cancelUrl + "?orderId=" + orderId);
        params.put("fail_url", failUrl + "?orderId=" + orderId);
        return params;
    }

    // 카카오페이 결제 승인 API
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "kakaoPayApi", fallbackMethod = "approveApiFallback")
    @io.github.resilience4j.retry.annotation.Retry(name = "kakaoPayApi")
    public Optional<KaKaoPayApproveDTO.KaKaoApiResponse> requestPaymentApprove(
            KaKaoPayApproveDTO.Parameter parameter, KakaoPayData kakaoPayData) {

        // [부하 테스트용] 인위적 지연 발생
        if (paymentDelayMs > 0) {
            try {
                log.info("[Stress Test] Injecting artificial delay: {}ms", paymentDelayMs);
                Thread.sleep(paymentDelayMs);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }
        }

        try {
            log.info("카카오페이 승인 요청 호출 - orderId: {}, tid: {}", parameter.getOrderId(), kakaoPayData.getTid());

            KaKaoPayApproveDTO.KaKaoApiResponse response = restClient.post()
                    .uri(APPROVE_URL)
                    .headers(h -> h.addAll(getHeaders()))
                    .body(getApproveParams(parameter, kakaoPayData))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.error("카카오페이 승인 API 에러 응답 - Status: {}", res.getStatusCode());
                        throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);
                    })
                    .body(KaKaoPayApproveDTO.KaKaoApiResponse.class);

            log.info("카카오페이 승인 응답 수신 - orderId: {}", parameter.getOrderId());
            return Optional.ofNullable(response);

        } catch (ResourceAccessException e) {
            if (e.getCause() instanceof SocketTimeoutException) {
                log.warn("카카오페이 결제 승인 API 타임아웃 발생 -> 상태 조회 시도 - orderId: {}", parameter.getOrderId());

                if (kakaoPayData.getTid() != null && !kakaoPayData.getTid().isEmpty()) {
                    Optional<KaKaoPayApproveDTO.KaKaoApiResponse> inquiryResult = inquirePaymentStatus(
                            kakaoPayData.getTid())
                            .filter(res -> "SUCCESS_PAYMENT".equals(res.getStatus()));

                    if (inquiryResult.isPresent()) {
                        log.info("상태 조회 결과 결제 성공 확인 - orderId: {}", parameter.getOrderId());
                        return inquiryResult; // 보상 성공 시 정상 리턴 (성공으로 카운트)
                    }
                }
            }
            log.error("카카오페이 결제 승인 API 최종 실패 - orderId: {}, error: {}", parameter.getOrderId(), e.getMessage());
            throw e; // 보상 실패 시 예외 투척 (서킷 브레이커 실패 카운트 증가)
        } catch (Exception e) {
            log.error("카카오페이 결제 승인 중 예상치 못한 오류 - orderId: {}, error: {}", parameter.getOrderId(), e.getMessage());
            throw e; // 서킷 브레이커 실패 카운트 증가
        }
    }

    private Optional<KaKaoPayApproveDTO.KaKaoApiResponse> approveApiFallback(
            KaKaoPayApproveDTO.Parameter parameter, KakaoPayData kakaoPayData, Exception e) {
        log.error("서킷 브레이커 작동: 승인 요청 차단/취소 - orderId: {}, 사유: {}", parameter.getOrderId(), e.getMessage());
        return Optional.empty();
    }

    private Map<String, String> getApproveParams(KaKaoPayApproveDTO.Parameter parameter, KakaoPayData kakaoPayData) {
        Map<String, String> params = new HashMap<>();
        params.put("cid", cid);
        params.put("tid", kakaoPayData.getTid());
        params.put("partner_order_id", parameter.getOrderId());
        params.put("partner_user_id", String.valueOf(kakaoPayData.getBuyer().getId()));
        params.put("pg_token", parameter.getPgToken());
        return params;
    }

    // 카카오페이 결제 취소 API
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "kakaoPayApi", fallbackMethod = "cancelApiFallback")
    @io.github.resilience4j.retry.annotation.Retry(name = "kakaoPayApi")
    public Optional<KaKaoPayCancelDTO.KakaoApiResponse> requestPaymentCancel(KaKaoPayCancelDTO.Parameter parameter) {

        try {
            return Optional.ofNullable(restClient.post()
                    .uri(CANCEL_API_URL)
                    .headers(h -> h.addAll(getHeaders()))
                    .body(getCancelParams(parameter))
                    .retrieve()
                    .onStatus(HttpStatusCode::is4xxClientError, (req, res) -> {
                        log.error("카카오페이 취소 API 4xx 에러 - tid: {}, Status: {}", parameter.getTid(), res.getStatusCode());
                        throw new BusinessException(Code.INVALID_KAKAO_API_RESPONSE);
                    })
                    .onStatus(HttpStatusCode::is5xxServerError, (req, res) -> {
                        log.error("카카오페이 취소 API 5xx 에러 - tid: {}, Status: {}", parameter.getTid(), res.getStatusCode());
                        throw new ResourceAccessException("카카오페이 서버 에러");
                    })
                    .body(KaKaoPayCancelDTO.KakaoApiResponse.class));
        } catch (Exception e) {
            log.error("카카오페이 취소 최종 실패 - tid: {}, error: {}", parameter.getTid(), e.getMessage());
            throw e; // 서킷 브레이커 인지를 위해 예외 투척
        }
    }

    private Optional<KaKaoPayCancelDTO.KakaoApiResponse> cancelApiFallback(
            KaKaoPayCancelDTO.Parameter parameter, Exception e) {
        log.error("서킷 브레이커 작동: 카카오페이 취소 API 차단 - tid: {}, error: {}", parameter.getTid(), e.getMessage());
        return Optional.empty();
    }

    private Map<String, String> getCancelParams(KaKaoPayCancelDTO.Parameter parameter) {
        Map<String, String> params = new HashMap<>();
        params.put("cid", cid);
        params.put("tid", parameter.getTid());
        params.put("cancel_amount", String.valueOf(parameter.getCancelAmount()));
        params.put("cancel_tax_free_amount",
                String.valueOf(parameter.getCancelTaxFreeAmount() != null ? parameter.getCancelTaxFreeAmount() : 0));
        if (parameter.getCancelReason() != null && !parameter.getCancelReason().isEmpty()) {
            params.put("cancel_reason", parameter.getCancelReason());
        }
        return params;
    }

    // 결제 상태 조회 API
    public Optional<KaKaoPayApproveDTO.KaKaoApiResponse> inquirePaymentStatus(String tid) {
        try {
            return Optional.ofNullable(restClient.get()
                    .uri(INQUIRY_API_URL + "?cid={cid}&tid={tid}", cid, tid)
                    .headers(h -> h.addAll(getHeaders()))
                    .retrieve()
                    .onStatus(HttpStatusCode::isError, (req, res) -> {
                        log.warn("카카오페이 상태 조회 API 에러 응답 - tid: {}, Status: {}", tid, res.getStatusCode());
                    })
                    .body(KaKaoPayApproveDTO.KaKaoApiResponse.class));
        } catch (Exception e) {
            log.warn("카카오페이 결제 상태 조회 중 오류 발생 - tid: {}, error: {}", tid, e.getMessage());
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
