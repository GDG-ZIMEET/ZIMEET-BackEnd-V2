package com.gdg.z_meet.domain.fcm.service.payment;

import com.gdg.z_meet.domain.fcm.service.producer.FcmMessageProducer;
import com.gdg.z_meet.domain.order.entity.enums.ProductType;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmPaymentMessageServiceImpl implements FcmPaymentMessageService {

    private final FcmMessageProducer fcmMessageProducer;

    @Override
    public void messagingPaymentSuccess(Long userId, ProductType productType, Long totalPrice) {
        String title = "🥳 결제가 성공적으로 완료되었습니다!";
        String body = String.format("[%s] 상품 구매가 완료되었습니다. 이용해 주셔서 감사합니다.", productType.getDesc());

        if (productType == ProductType.TICKET || productType == ProductType.SEASON) {
            body = String.format("ZI-MEET Plus 멤버십 또는 티켓 구매가 완료되었습니다. 지금 바로 이용해보세요! ✨");
        } else {
            body = String.format("팀 하이가 충전되었습니다. 마음에 드는 상대에게 하이를 보내보세요! ❤️");
        }

        fcmMessageProducer.sendSingleMessage(userId, title, body);
        log.info("결제 성공 FCM 메시지 이벤트를 발행했습니다. userId: {}, productType: {}", userId, productType);
    }
}