package com.gdg.z_meet.domain.order.service.recovery;

import com.gdg.z_meet.domain.order.entity.PaymentRecovery;
import com.gdg.z_meet.domain.order.repository.PaymentRecoveryRepository;
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

    private final PaymentRecoveryRepository paymentRecoveryRepository;
    private final PaymentRecoveryService paymentRecoveryService;

    private static final int BATCH_SIZE = 10;

    /**
     * 보상 트랜잭션 처리 스케줄러
     * 각 task는 개별 트랜잭션으로 처리하여, 하나의 task 실패가 다른 task에 영향을 주지 않도록 함
     */
    @Scheduled(fixedDelay = 5000)
    public void processPendingRecoveries() {
        // 1. 처리할 대상 조회 (SKIP LOCKED로 동시성 제어)
        List<PaymentRecovery> tasks = paymentRecoveryRepository.findTasksToProcess(
                LocalDateTime.now(), PageRequest.of(0, BATCH_SIZE));

        if (tasks.isEmpty()) {
            return;
        }

        log.info("보상 처리 대상 {}건 발견", tasks.size());

        // 각 task를 개별 트랜잭션으로 처리
        for (PaymentRecovery task : tasks) {
            paymentRecoveryService.processRecovery(task);
        }
    }
}