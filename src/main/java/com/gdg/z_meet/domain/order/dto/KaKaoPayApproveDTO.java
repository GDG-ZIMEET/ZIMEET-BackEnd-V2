package com.gdg.z_meet.domain.order.dto;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;

@Schema(name = "KaKaoPayApproveDTO.Request")
public class KaKaoPayApproveDTO {

    @NoArgsConstructor
    @Getter
    @Schema(description = "카카오페이 결제 승인 요청 DTO")
    public static class Request {
        @NotNull
        @Schema(description = "결제 최종 완료를 위한 토큰")
        private String pgToken;
        @NotNull
        @Schema(description = "주문 번호 (세션에서 결제 정보 탐색 Key)")
        private String orderId;
    }

    @Getter
    @Builder
    public static class Parameter {
        private Long userId;
        private String pgToken;    // 결제 최종 완료를 위한 토큰
        private String orderId;    // 주문 번호 (결제 정보 탐색 Key)
    }

    @Getter
    @Builder
    public static class KaKaoApiResponse {
        private String aid;                  // 요청 고유 번호
        private String tid;                  // 결제 고유 번호
        private String cid;                  // 가맹점 코드
        private String partner_order_id;     // 주문 번호 (DB 저장)
        private String partner_user_id;      // 구매자 ID
        private String product_type;
        private Amount amount;               // 결제 금액 관련
        private String approved_at;          // 결제 승인 시각
    }

    @Getter
    @Builder
    public static class Amount {
        private Long total;    // 총 결제 금액
        private Long vat;     // 부가세 금액
    }

    @Getter
    @Builder
    public static class Response {
        private String orderId;            // 주문 ID (카카오페이와 내부 주문 관리용)
        private String approvedAt;
    }
}
