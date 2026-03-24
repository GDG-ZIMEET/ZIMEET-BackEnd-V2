package com.gdg.z_meet.domain.order.config;

import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnErrorEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnFailureRateExceededEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnSlowCallRateExceededEvent;
import io.github.resilience4j.circuitbreaker.event.CircuitBreakerOnStateTransitionEvent;
import io.github.resilience4j.retry.Retry;
import io.github.resilience4j.retry.RetryRegistry;
import io.github.resilience4j.retry.event.RetryOnErrorEvent;
import io.github.resilience4j.retry.event.RetryOnRetryEvent;
import io.github.resilience4j.retry.event.RetryOnSuccessEvent;
import jakarta.annotation.PostConstruct;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;

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
        CircuitBreaker cb = circuitBreakerRegistry.circuitBreaker("kakaoPayApi");
        cb.getEventPublisher()
                .onStateTransition((CircuitBreakerOnStateTransitionEvent event) -> {
                    log.warn("[Circuit Breaker] 상태 전환: {} -> {}, 이름: {}",
                            event.getStateTransition().getFromState(),
                            event.getStateTransition().getToState(),
                            event.getCircuitBreakerName());
                })
                .onFailureRateExceeded((CircuitBreakerOnFailureRateExceededEvent event) -> {
                    log.error("[Circuit Breaker] 실패율 임계값 초과: {}%, 이름: {}",
                            event.getFailureRate(),
                            event.getCircuitBreakerName());
                })
                .onSlowCallRateExceeded((CircuitBreakerOnSlowCallRateExceededEvent event) -> {
                    log.warn("[Circuit Breaker] 느린 호출 비율 초과: {}%, 이름: {}",
                            event.getSlowCallRate(),
                            event.getCircuitBreakerName());
                })
                .onError((CircuitBreakerOnErrorEvent event) -> {
                    log.error("[Circuit Breaker] 에러 발생: {}, 이름: {}",
                            event.getThrowable().getMessage(),
                            event.getCircuitBreakerName());
                });

        // Retry 이벤트 리스너 등록
        Retry retry = retryRegistry.retry("kakaoPayApi");
        retry.getEventPublisher()
                .onRetry((RetryOnRetryEvent event) -> {
                    log.warn("[Retry] 재시도 시도 중: 시도 횟수={}, 에러={}",
                            event.getNumberOfRetryAttempts(),
                            event.getLastThrowable().getMessage());
                })
                .onSuccess((RetryOnSuccessEvent event) -> {
                    log.info("[Retry] 재시도 성공: 총 시도 횟수={}",
                            event.getNumberOfRetryAttempts());
                })
                .onError((RetryOnErrorEvent event) -> {
                    log.error("[Retry] 재시도 최종 실패: 총 시도 횟수={}, 에러={}",
                            event.getNumberOfRetryAttempts(),
                            event.getLastThrowable().getMessage());
                });
    }
}