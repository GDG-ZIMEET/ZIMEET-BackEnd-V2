package com.gdg.z_meet.domain.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.retry.RetryRegistry;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

import jakarta.annotation.PostConstruct;

/**
 * Resilience4j 이벤트 리스너 설정
 * Circuit Breaker 상태 변화 및 Retry 이벤트를 로깅하여 모니터링 가능하도록 함
 */
@Slf4j
@Configuration
@RequiredArgsConstructor
public class Resilience4jEventConfig {

    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final RetryRegistry retryRegistry;

    @PostConstruct
    public void registerEventListeners() {
        // Circuit Breaker 이벤트 리스너 등록
        circuitBreakerRegistry.circuitBreaker("kakaoPayApi").getEventPublisher()
                .onStateTransition(event -> {
                    log.warn("[Circuit Breaker] 상태 전환: {} -> {}, 이유: {}",
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState(),
                            event.getCircuitBreakerName());
                })
                .onFailureRateExceeded(event -> {
                    log.error("[Circuit Breaker] 실패율 임계값 초과: {}%, CircuitBreaker: {}",
                            event.getFailureRate(),
                            event.getCircuitBreakerName());
                })
                .onSlowCallRateExceeded(event -> {
                    log.warn("[Circuit Breaker] 느린 호출 비율 초과: {}%, CircuitBreaker: {}",
                            event.getSlowCallRate(),
                            event.getCircuitBreakerName());
                })
                .onError(event -> {
                    log.error("[Circuit Breaker] 에러 발생: {}, CircuitBreaker: {}",
                            event.getThrowable().getMessage(),
                            event.getCircuitBreakerName());
                });

        // Retry 이벤트 리스너 등록
        retryRegistry.retry("kakaoPayApi").getEventPublisher()
                .onRetry(event -> {
                    log.warn("[Retry] 재시도 시도 중: 시도 횟수={}, 에러={}",
                            event.getNumberOfRetryAttempts(),
                            event.getLastThrowable().getMessage());
                })
                .onSuccess(event -> {
                    log.info("[Retry] 재시도 성공: 총 시도 횟수={}",
                            event.getNumberOfRetryAttempts());
                })
                .onError(event -> {
                    log.error("[Retry] 재시도 최종 실패: 총 시도 횟수={}, 에러={}",
                            event.getNumberOfRetryAttempts(),
                            event.getLastThrowable().getMessage());
                });
    }
}