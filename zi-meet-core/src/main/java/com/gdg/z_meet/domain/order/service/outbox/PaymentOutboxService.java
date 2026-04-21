package com.gdg.z_meet.domain.order.service.outbox;

import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.domain.order.service.approve.KakaoPayApproveService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@Profile("!local")
@RequiredArgsConstructor
public class PaymentOutboxService {

    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final KakaoPayApproveService kakaoPayApproveService;

    @Transactional
    public void publishEvent(KakaoPayData data) {
        try {
            KaKaoPayApproveDTO.Parameter parameter = KaKaoPayApproveDTO.Parameter.builder()
                    .orderId(data.getOrderId())
                    .userId(data.getBuyer().getId())
                    .pgToken(data.getPgToken())
                    .build();

            log.info("결제 승인 서비스 직접 호출 시작 - orderId: {}", data.getOrderId());
            kakaoPayApproveService.processApproval(parameter);

            // 상태 업데이트
            data.markAsPublished();
            kakaoPayDataRepository.save(data);

            log.info("아웃박스 이벤트 처리 완료 - orderId: {}", data.getOrderId());

        } catch (Exception e) {
            log.error("아웃박스 이벤트 처리 실패 - orderId: {}, error: {}", data.getOrderId(), e.getMessage());
            data.increaseRetryCount();
            if (data.getPublishRetryCount() > 5) {
                data.markAsFailed();
            }
            kakaoPayDataRepository.save(data);
        }
    }
}