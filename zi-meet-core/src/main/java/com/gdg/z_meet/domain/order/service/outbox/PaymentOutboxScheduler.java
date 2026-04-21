package com.gdg.z_meet.domain.order.service.outbox;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.OutboxStatus;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.util.List;

@Slf4j
@Service
@RequiredArgsConstructor
@org.springframework.context.annotation.Profile("worker")
public class PaymentOutboxScheduler {

    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final PaymentOutboxService outboxService;

    /**
     * 아웃박스 테이블 폴링 및 메시지 발행
     * 1초마다 실행
     */
    @Scheduled(fixedDelay = 1000)
    public void processOutbox() {
        List<KakaoPayData> waitingList = kakaoPayDataRepository.findByOutboxStatusAndStatus(
                OutboxStatus.INIT.name(), PaymentStatus.PROCESSING.name());

        if (waitingList.isEmpty()) {
            return;
        }

        log.debug("아웃박스 폴링 - 발행 대기 건수: {}", waitingList.size());

        for (KakaoPayData data : waitingList) {
            outboxService.publishEvent(data);
        }
    }
}