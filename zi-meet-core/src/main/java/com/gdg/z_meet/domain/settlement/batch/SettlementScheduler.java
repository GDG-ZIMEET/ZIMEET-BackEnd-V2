package com.gdg.z_meet.domain.settlement.batch;

import com.gdg.z_meet.domain.settlement.service.SettlementService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Slf4j
@Component
@RequiredArgsConstructor
public class SettlementScheduler {

    private final SettlementService settlementService;

    // 매일 새벽 3시에 정산 실행 (소급 정산)
    @Scheduled(cron = "0 0 3 * * *")
    public void runSettlement() {
        log.info("Settlement Batch started...");
        try {
            settlementService.processDelayedSettlement();
            log.info("Settlement Batch finished successfully.");
        } catch (Exception e) {
            log.error("Settlement Batch failed with error: ", e);
            // 알림 시스템(Slack 등) 연동 가능 지점
        }
    }
}
