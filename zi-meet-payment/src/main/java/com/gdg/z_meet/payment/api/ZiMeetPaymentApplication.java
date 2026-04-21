package com.gdg.z_meet.payment.api;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.scheduling.annotation.EnableScheduling;

/**
 * 결제 서버 엔트리 포인트.
 *
 * Responsibilities:
 *  - 결제 승인/취소/환불 (KakaoPay 연동)
 *  - 원장(Ledger) append
 *  - 정산 배치 (Spring Batch 미사용, @Scheduled + chunk 루프 + settlement_run 체크포인트)
 *  - PG 대사 배치
 *  - Outbox Relay (Redis Streams 로 이벤트 발행)
 *  - Orphan 스윕 배치
 *
 * Profiles:
 *  - payment-api    : REST 엔드포인트만 활성화
 *  - payment-worker : @Scheduled 배치 + Outbox Relay + Circuit Breaker 상태 관리
 *
 * 같은 JAR 를 두 프로파일로 기동해 API/Worker 를 배포 분리한다.
 */
@SpringBootApplication
@ComponentScan(basePackages = {
        "com.gdg.z_meet.payment",
        "com.gdg.z_meet.common"
})
@EnableScheduling
public class ZiMeetPaymentApplication {

    public static void main(String[] args) {
        SpringApplication.run(ZiMeetPaymentApplication.class, args);
    }
}
