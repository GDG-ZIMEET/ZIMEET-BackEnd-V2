package com.gdg.z_meet.domain.fcm.service.payment;

import com.gdg.z_meet.domain.fcm.service.core.FcmDomainMessageService;
import com.gdg.z_meet.domain.order.entity.enums.ProductType;

public interface FcmPaymentMessageService extends FcmDomainMessageService {

    void messagingPaymentSuccess(Long userId, ProductType productType, Long totalPrice);
}