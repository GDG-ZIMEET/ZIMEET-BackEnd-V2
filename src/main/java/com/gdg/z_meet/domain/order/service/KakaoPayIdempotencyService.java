package com.gdg.z_meet.domain.order.service;

import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import java.time.Duration;

@Slf4j
@Service
@RequiredArgsConstructor
public class KakaoPayIdempotencyService {

    private final RedisTemplate<String, Object> redisTemplate;
    
    private static final String IDEMPOTENCY_KEY_PREFIX = "idempotency:";
    private static final String PROCESSING_KEY_PREFIX = "processing:";
    private static final Duration IDEMPOTENCY_TTL = Duration.ofHours(24);
    private static final Duration PROCESSING_TTL = Duration.ofMinutes(5);

    /**
     * 멱등성 키 전체 검증 및 처리
     * @param key 멱등성 키
     * @param payload 현재 요청의 Payload
     * @return 검증 결과
     */
    public IdempotencyValidationResult validate(String key, String payload) {
        if (key == null || key.isEmpty()) {
            return IdempotencyValidationResult.empty();
        }

        // 422: Payload 불일치 체크
        String previousPayload = getCachedPayload(key);
        if (previousPayload != null && !previousPayload.equals(payload)) {
            log.warn("요청 본문이 이전 요청과 다릅니다 - 멱등성 키: {}", key);
            throw new BusinessException(Code.IDEMPOTENCY_PAYLOAD_MISMATCH);
        }

        // 이미 완료된 요청 확인
        Object cachedResponse = checkAndGetCachedResponse(key);
        if (cachedResponse != null) {
            log.info("중복 요청 처리 - 멱등성 키: {}", key);
            return IdempotencyValidationResult.cached(cachedResponse);
        }

        // SETNX 로 원자적 처리 중 표시 및 경합 상태 해결
        if (!markAsProcessing(key)) {
            log.warn("동일한 요청이 처리 중입니다 - 멱등성 키: {}", key);
            throw new BusinessException(Code.IDEMPOTENCY_CONFLICT);
        }

        cachePayload(key, payload);
        return IdempotencyValidationResult.processing();
    }

    // ========== Private Helper Methods ==========
    
    /**
     * 처리 중 표시 제거 (원자적 연산)
     * @param key 멱등성 키
     */
    public void unmarkAsProcessing(String key) {
        if (key == null || key.isEmpty()) {
            return;
        }

        String processingKey = PROCESSING_KEY_PREFIX + key;
        
        // 값이 존재하는 경우에만 삭제 (원자적 연산)
        if (Boolean.TRUE.equals(redisTemplate.hasKey(processingKey))) {
            Boolean deleted = redisTemplate.delete(processingKey);
            if (Boolean.TRUE.equals(deleted)) {
                log.debug("요청 처리 완료 - 멱등성 키: {}", key);
            }
        }
    }

    /**
     * 응답을 캐시에 저장
     * @param key 멱등성 키
     * @param response 캐시할 응답
     */
    public void cacheResponse(String key, Object response) {
        if (key == null || key.isEmpty() || response == null) {
            return;
        }

        String redisKey = IDEMPOTENCY_KEY_PREFIX + key;
        redisTemplate.opsForValue().set(redisKey, response, IDEMPOTENCY_TTL);
        log.debug("응답 캐시 저장 - 멱등성 키: {}", key);
    }

    /**
     * 캐시된 응답 확인
     */
    private Object checkAndGetCachedResponse(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        String redisKey = IDEMPOTENCY_KEY_PREFIX + key;
        Object cachedResponse = redisTemplate.opsForValue().get(redisKey);
        
        if (cachedResponse != null) {
            log.info("중복 요청 감지 - 멱등성 키: {}", key);
            return cachedResponse;
        }
        
        return null;
    }

    /**
     * 이전 요청의 Payload 확인
     */
    private String getCachedPayload(String key) {
        if (key == null || key.isEmpty()) {
            return null;
        }

        String payloadKey = IDEMPOTENCY_KEY_PREFIX + key + ":payload";
        return (String) redisTemplate.opsForValue().get(payloadKey);
    }

    /**
     * Payload 캐시 저장
     */
    private void cachePayload(String key, String payload) {
        if (key == null || key.isEmpty() || payload == null) {
            return;
        }

        String payloadKey = IDEMPOTENCY_KEY_PREFIX + key + ":payload";
        redisTemplate.opsForValue().set(payloadKey, payload, IDEMPOTENCY_TTL);
        log.debug("Payload 캐시 저장 - 멱등성 키: {}", key);
    }

    /**
     * 멱등 처리 중임을 표시하는 메서드
     * @param key 멱등성 키
     * @return SETNX 성공 여부 (true: 성공, false: 이미 처리 중)
     */
    private boolean markAsProcessing(String key) {
        if (key == null || key.isEmpty()) {
            return false;
        }

        String processingKey = PROCESSING_KEY_PREFIX + key;
        
        // 아직 처리 중 표시가 없으면 새로 표시하고, 이미 있으면 중복 요청으로 간주
        Boolean setIfAbsent = redisTemplate.opsForValue().setIfAbsent(processingKey, "processing", PROCESSING_TTL);
        
        if (Boolean.TRUE.equals(setIfAbsent)) {
            log.debug("요청 처리 시작 - 멱등성 키: {}", key);
            return true;
        } else {
            log.warn("이미 처리 중인 요청 - 멱등성 키: {}", key);
            return false;
        }
    }

    /**
     * 멱등성 검증 결과
     */
    public static class IdempotencyValidationResult {
        private final boolean isCached;
        private final boolean isProcessing;
        private final Object cachedResponse;

        private IdempotencyValidationResult(boolean isCached, boolean isProcessing, Object cachedResponse) {
            this.isCached = isCached;
            this.isProcessing = isProcessing;
            this.cachedResponse = cachedResponse;
        }

        public static IdempotencyValidationResult empty() {
            return new IdempotencyValidationResult(false, false, null);
        }

        public static IdempotencyValidationResult cached(Object response) {
            return new IdempotencyValidationResult(true, false, response);
        }

        public static IdempotencyValidationResult processing() {
            return new IdempotencyValidationResult(false, true, null);
        }

        public boolean isCached() { return isCached; }
        public boolean isProcessing() { return isProcessing; }
        public Object getCachedResponse() { return cachedResponse; }
    }
}
