package com.gdg.z_meet.domain.order.service.test;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

@Slf4j
@Service
@RequiredArgsConstructor
public class PaymentPoolTestService {

    /**
     * REQUIRES_NEW를 사용하여 짧은 트랜잭션을 실행합니다.
     * 비즈니스 커넥션 풀을 잠시만 점유합니다.
     */
    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void executeShortTransaction(String step, String orderId) {
        log.info("[Pool Test] {} 시작 - OrderId: {}", step, orderId);
        // DB 작업 시뮬레이션
        try {
            Thread.sleep(50); // 50ms DB 작업
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("[Pool Test] {} 완료 - OrderId: {}", step, orderId);
    }

    /**
     * 단일 트랜잭션 내에서 외부 API 호출을 시뮬레이션합니다. (Before 케이스)
     * 이 메서드는 호출하는 쪽에서 @Transactional이 걸려 있어야 합니다.
     */
    public void executeLongTransactionWithExternalApi(String orderId) {
        log.info("[Pool Test] DB 작업 1 시작 - OrderId: {}", orderId);
        // DB 작업 시뮬레이션
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
        }

        log.info("[Pool Test] 외부 API 호출 시작 (5초 대기) - OrderId: {}", orderId);
        try {
            Thread.sleep(5000); // 5초 외부 API 대기 (커넥션 점유 중!)
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }

        log.info("[Pool Test] DB 작업 2 시작 - OrderId: {}", orderId);
        try {
            Thread.sleep(50);
        } catch (InterruptedException e) {
        }
        log.info("[Pool Test] 전체 프로세스 완료 - OrderId: {}", orderId);
    }
}
