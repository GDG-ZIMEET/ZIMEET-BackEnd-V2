package com.gdg.z_meet.domain.order.controller;

import com.gdg.z_meet.domain.order.service.locking.KakaoPayLockService;
import com.gdg.z_meet.domain.order.service.test.PaymentPoolTestService;
import com.gdg.z_meet.global.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequestMapping("/api/test/pool")
@RequiredArgsConstructor
@Slf4j
@Profile("local")
@Tag(name = "Payment Pool Test", description = "커넥션 풀 분리 및 트랜잭션 최적화 비교 테스트용 API")
public class PaymentPoolTestController {

    private final KakaoPayLockService kakaoPayLockService;
    private final PaymentPoolTestService testService;

    /**
     * [Before] 개선 전 구조 시뮬레이션
     * - 단일 트랜잭션 내에서 외부 API 호출 (5초)
     * - 비즈니스 커넥션 풀의 커넥션을 5초 동안 점유
     */
    @Operation(summary = "[Before] 개선 전 구조", description = "단일 트랜잭션 내에서 5초간 외부 API를 호출하여 커넥션 풀 고갈을 유발합니다.")
    @PostMapping("/before")
    @Transactional // 전체가 하나의 트랜잭션
    public Response<Map<String, Object>> testBefore(@RequestBody Map<String, String> request) {
        String orderId = request.getOrDefault("orderId", "BEFORE_TEST_" + System.currentTimeMillis());
        log.info("[Pool Test] BEFORE 요청 수신 - OrderId: {}", orderId);

        // 락 획득 (Lock Pool 사용)
        return kakaoPayLockService.executeWithLock(orderId, () -> {
            testService.executeLongTransactionWithExternalApi(orderId);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("mode", "BEFORE");
            return Response.ok(response);
        });
    }

    /**
     * [After] 개선 후 구조 시뮬레이션
     * - 트랜잭션 분리 (REQUIRES_NEW)
     * - 외부 API 호출 시에는 DB 커넥션 미사용
     */
    @Operation(summary = "[After] 개선 후 구조", description = "트랜잭션을 분리하여 외부 API 호출 시에는 커넥션을 반납합니다.")
    @PostMapping("/after")
    // @Transactional 없음!
    public Response<Map<String, Object>> testAfter(@RequestBody Map<String, String> request) {
        String orderId = request.getOrDefault("orderId", "AFTER_TEST_" + System.currentTimeMillis());
        log.info("[Pool Test] AFTER 요청 수신 - OrderId: {}", orderId);

        // 락 획득 (Lock Pool 사용)
        return kakaoPayLockService.executeWithLock(orderId, () -> {
            // 1. 첫 번째 트랜잭션 (Business Pool 점유 후 즉시 반납)
            testService.executeShortTransaction("STEP 1", orderId);

            // 2. 외부 API 호출 시뮬레이션 (DB 커넥션 없음!)
            log.info("[Pool Test] 외부 API 호출 시작 (5초 대기) - OrderId: {}", orderId);
            try {
                Thread.sleep(5000);
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
            }

            // 3. 두 번째 트랜잭션 (Business Pool 점유 후 즉시 반납)
            testService.executeShortTransaction("STEP 2", orderId);

            Map<String, Object> response = new HashMap<>();
            response.put("status", "SUCCESS");
            response.put("mode", "AFTER");
            return Response.ok(response);
        });
    }
}
