package com.gdg.z_meet.domain.order.service.recovery;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.data.domain.PageRequest;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

@Slf4j
@Component
@RequiredArgsConstructor
@Profile("worker")
public class PaymentRecoveryScheduler {

    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final PaymentRecoveryService paymentRecoveryService;

    private static final int BATCH_SIZE = 10;

    /**
     * 보상 트랜잭션 처리 스케줄러 (Merged into KakaoPayData)
     */
    @Scheduled(fixedDelay = 5000)
    public void processPendingRecoveries() {
        // 1. 처리할 대상 조회 (KakaoPayData 테이블 내 recovery_status 확인)
        List<KakaoPayData> tasks = kakaoPayDataRepository.findRecoveryTasksToProcess(
                LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));

        if (tasks.isEmpty()) {
            return;
        }

        log.info("보상 처리 대상 {}건 발견 (Merged Outbox)", tasks.size());

        // 각 task를 개별 트랜잭션으로 처리
        for (KakaoPayData task : tasks) {
            paymentRecoveryService.processRecovery(task);
        }
    }
}