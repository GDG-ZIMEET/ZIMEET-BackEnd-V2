package com.gdg.z_meet.domain.order.dto;

import io.swagger.v3.oas.annotations.media.Schema;
import jakarta.validation.constraints.NotNull;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Schema(name = "KaKaoPayCancelDTO.Request")
public class KaKaoPayCancelDTO {

    @NoArgsConstructor
    @Getter
    @Schema(description = "카카오페이 결제 취소 요청 DTO")
    public static class Request {
        @NotNull
        @Schema(description = "주문 번호")
        private String orderId;
        
        @Schema(description = "취소 사유")
        private String cancelReason;
    }

    @Getter
    @Builder
    public static class Parameter {
        private String orderId;
        private String tid;
        private Long cancelAmount;
        private Long cancelTaxFreeAmount;
        private String cancelReason;
    }

    @Getter
    @Builder
    public static class KakaoApiResponse {
        private String status;                // 결제 상태
        private String canceled_at;           // 취소 시각
    }

    @Getter
    @Builder
    public static class Response {
        private String orderId;
        private String canceledAt;
        private String status;
    }
}

