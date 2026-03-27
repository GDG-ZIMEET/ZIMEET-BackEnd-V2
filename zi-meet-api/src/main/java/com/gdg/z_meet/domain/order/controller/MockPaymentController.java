package com.gdg.z_meet.domain.order.controller;

import com.gdg.z_meet.domain.order.service.idempotency.KakaoPayIdempotencyService;
import com.gdg.z_meet.global.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.Parameter;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Profile;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDateTime;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.atomic.AtomicLong;

/**
 * Graceful Shutdown 테스트를 위한 Mock Payment Controller
 * 실제 카카오페이 API 호출 없이 결제 프로세스를 시뮬레이션합니다.
 * 
 * @Profile("local") - 로컬 환경에서만 활성화
 */
@RestController
@RequestMapping("/api/test/payment")
@RequiredArgsConstructor
@Slf4j
@Profile("local")
@Tag(name = "Mock Payment (Test Only)", description = "Graceful Shutdown 테스트용 Mock 결제 API")
public class MockPaymentController {

    private final KakaoPayIdempotencyService idempotencyService;
    private static final AtomicLong requestCounter = new AtomicLong(0);

    /**
     * 긴 처리 시간을 시뮬레이션하는 결제 준비 엔드포인트
     * Graceful Shutdown 테스트에 사용됩니다.
     */
    @Operation(summary = "Mock 결제 준비 (느린 처리)", description = "3초 처리 시간을 시뮬레이션하여 Graceful Shutdown 동작을 테스트합니다.")
    @PostMapping("/ready")
    public Response<Map<String, Object>> mockPaymentReady(
            @RequestBody Map<String, Object> request,
            @Parameter(description = "멱등성 키", example = "test-idem-key-001") @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey) {

        long requestId = requestCounter.incrementAndGet();
        log.info("[Mock Payment] 요청 시작 - RequestId: {}, IdempotencyKey: {}", requestId, idempotencyKey);

        // 멱등성 검증
        if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
            var validationResult = idempotencyService.validate(
                    idempotencyKey,
                    request.toString());

            if (validationResult.isCached()) {
                log.info("[Mock Payment] 캐시된 응답 반환 - RequestId: {}, IdempotencyKey: {}",
                        requestId, idempotencyKey);
                return Response.ok((Map<String, Object>) validationResult.getCachedResponse());
            }
        }

        try {
            // 실제 결제 처리처럼 시간이 걸리는 작업 시뮬레이션 (3초)
            log.info("[Mock Payment] 처리 중... - RequestId: {}", requestId);
            Thread.sleep(3000);

            Map<String, Object> response = new HashMap<>();
            response.put("requestId", requestId);
            response.put("status", "SUCCESS");
            response.put("orderId", request.getOrDefault("orderId", "unknown"));
            response.put("amount", request.getOrDefault("amount", 0));
            response.put("processedAt", LocalDateTime.now().toString());
            response.put("idempotencyKey", idempotencyKey);
            response.put("message", "Mock payment processed successfully");

            // 멱등성 응답 캐싱
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                idempotencyService.cacheResponse(idempotencyKey, response);
            }

            log.info("[Mock Payment] 요청 완료 - RequestId: {}, Status: SUCCESS", requestId);
            return Response.ok(response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Mock Payment] 요청 중단됨 - RequestId: {}, Error: {}", requestId, e.getMessage());

            // 멱등성 처리 중 표시 제거
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                idempotencyService.unmarkAsProcessing(idempotencyKey);
            }

            throw new RuntimeException("Processing interrupted during graceful shutdown", e);
        } catch (Exception e) {
            log.error("[Mock Payment] 요청 실패 - RequestId: {}, Error: {}", requestId, e.getMessage());

            // 멱등성 처리 중 표시 제거
            if (idempotencyKey != null && !idempotencyKey.isEmpty()) {
                idempotencyService.unmarkAsProcessing(idempotencyKey);
            }

            throw e;
        }
    }

    /**
     * 빠른 처리를 시뮬레이션하는 엔드포인트 (비교용)
     */
    @Operation(summary = "Mock 결제 준비 (빠른 처리)", description = "100ms 처리 시간으로 정상 케이스를 테스트합니다.")
    @PostMapping("/ready-fast")
    public Response<Map<String, Object>> mockPaymentReadyFast(
            @RequestBody Map<String, Object> request) {

        long requestId = requestCounter.incrementAndGet();
        log.info("[Mock Payment Fast] 요청 처리 - RequestId: {}", requestId);

        try {
            Thread.sleep(100); // 100ms 대기

            Map<String, Object> response = new HashMap<>();
            response.put("requestId", requestId);
            response.put("status", "SUCCESS");
            response.put("orderId", request.getOrDefault("orderId", "unknown"));
            response.put("processedAt", LocalDateTime.now().toString());

            return Response.ok(response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Processing interrupted", e);
        }
    }

    /**
     * 헬스 체크 엔드포인트 (느린 응답)
     */
    @Operation(summary = "느린 헬스 체크", description = "5초 지연된 헬스 체크로 Graceful Shutdown 테스트")
    @GetMapping("/health-slow")
    public Response<Map<String, Object>> healthCheckSlow() {
        long requestId = requestCounter.incrementAndGet();
        log.info("[Mock Health] 헬스 체크 시작 - RequestId: {}", requestId);

        try {
            Thread.sleep(5000); // 5초 대기

            Map<String, Object> response = new HashMap<>();
            response.put("requestId", requestId);
            response.put("status", "UP");
            response.put("timestamp", LocalDateTime.now().toString());

            log.info("[Mock Health] 헬스 체크 완료 - RequestId: {}", requestId);
            return Response.ok(response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            log.error("[Mock Health] 헬스 체크 중단 - RequestId: {}", requestId);
            throw new RuntimeException("Health check interrupted", e);
        }
    }

    private final com.gdg.z_meet.domain.order.service.locking.KakaoPayLockService kakaoPayLockService;

    /**
     * 락 경합 모니터링 테스트를 위한 엔드포인트
     * 인증 없이 접근 가능하며, 실제 Named Lock을 사용하여 경합을 유발합니다.
     */
    @Operation(summary = "Mock 락 경합 테스트", description = "50ms의 짧은 로직을 수행하지만, Named Lock을 사용하여 다수 요청 시 경합을 유발합니다.")
    @PostMapping("/lock-test")
    public Response<Map<String, Object>> mockPaymentLockTest(
            @RequestBody Map<String, Object> request) {

        String orderId = (String) request.getOrDefault("orderId", "TEST_LOCK");
        long requestId = requestCounter.incrementAndGet();

        return kakaoPayLockService.executeWithLock(orderId, () -> {
            try {
                // 실제 비즈니스 로직은 매우 빠름 (50ms)
                // 하지만 락 때문에 대기 시간이 길어질 것임
                Thread.sleep(50);

                Map<String, Object> response = new HashMap<>();
                response.put("requestId", requestId);
                response.put("status", "SUCCESS");
                response.put("processedAt", LocalDateTime.now().toString());
                response.put("message", "Lock acquired and processed");

                return Response.ok(response);

            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                throw new RuntimeException("Interrupted", e);
            }
        });
    }

    @Operation(summary = "동적 테스트 시뮬레이터", description = "헤더(X-Simulated-Error-Rate, X-Simulated-Delay)를 통해 에러와 지연을 제어합니다.")
    @PostMapping("/simulate")
    @io.github.resilience4j.circuitbreaker.annotation.CircuitBreaker(name = "kakaoPayApi", fallbackMethod = "simulateFallback")
    public Response<Map<String, Object>> simulate(
            @RequestBody Map<String, Object> request,
            @RequestHeader(value = "X-Simulated-Error-Rate", defaultValue = "0") int errorRate,
            @RequestHeader(value = "X-Simulated-Delay", defaultValue = "0") int delayMs) {

        long requestId = requestCounter.incrementAndGet();

        try {
            // 1. 지연 시간 시뮬레이션
            if (delayMs > 0) {
                Thread.sleep(delayMs);
            }

            // 2. 에러율 시뮬레이션 (0~100)
            int randomValue = (int) (Math.random() * 100);
            if (randomValue < errorRate) {
                log.warn("[Simulate] 강제 에러 발생 - RequestId: {}, Rate: {}%", requestId, errorRate);
                throw new RuntimeException("Simulated External Service Error");
            }

            Map<String, Object> response = new HashMap<>();
            response.put("requestId", requestId);
            response.put("status", "SUCCESS");
            response.put("message", "Simulated success");

            return Response.ok(response);

        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
            throw new RuntimeException("Interrupted", e);
        }
    }

    private Response<Map<String, Object>> simulateFallback(
            Map<String, Object> request, int errorRate, int delayMs, Exception e) {
        log.error("[Simulate Fallback] 서킷 브레이커 작동 - RequestId: {}, Error: {}", request.get("orderId"), e.getMessage());
        Map<String, Object> response = new HashMap<>();
        response.put("status", "FALLBACK");
        response.put("message", "Circuit breaker fallback response");
        return Response.ok(response);
    }

    /**
     * 요청 카운터 조회 (모니터링용)
     */
    @GetMapping("/stats")
    public Response<Map<String, Object>> getStats() {
        Map<String, Object> stats = new HashMap<>();
        stats.put("totalRequests", requestCounter.get());
        stats.put("timestamp", LocalDateTime.now().toString());
        return Response.ok(stats);
    }
}
