package com.gdg.z_meet.domain.order.dto;

import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.ToString;

@Getter
@NoArgsConstructor
@ToString
public class KakaoPayWebhookDTO {
    private String cid;
    private String tid;
    private String partner_order_id;
    private String partner_user_id;
    private String status;
    private String approved_at;
    private Long amount;

    // KakaoPay sends various fields depending on the status.
    // For simplicity, we mainly need status, tid, and partner_order_id.
}
