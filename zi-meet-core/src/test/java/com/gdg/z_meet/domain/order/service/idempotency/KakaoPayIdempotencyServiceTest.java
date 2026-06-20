package com.gdg.z_meet.domain.order.service.idempotency;

import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.redis.core.script.DefaultRedisScript;

import java.time.Duration;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("KakaoPayIdempotencyService 테스트")
class KakaoPayIdempotencyServiceTest {

    @Mock
    private RedisTemplate<String, Object> redisTemplate;

    @Mock
    private ValueOperations<String, Object> valueOperations;

    private KakaoPayIdempotencyService idempotencyService;
    private static final String TEST_IDEMPOTENCY_KEY = "test-key-123";
    private static final String TEST_NAMESPACED_KEY = "userId123:test-key-123";
    private static final String TEST_PAYLOAD = "orderId:pgToken";

    @BeforeEach
    void setUp() {
        idempotencyService = new KakaoPayIdempotencyService(redisTemplate);
    }

    @Test
    @DisplayName("멱등성 키 없으면 빈 결과 반환")
    void 멱등성_키_없으면_빈_결과_반환() {
        // when
        var result = idempotencyService.validate(null, TEST_PAYLOAD);

        // then
        assertThat(result.isCached()).isFalse();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("빈 키로 재요청 - 무시")
    void 빈_키로_재요청_무시() {
        // when
        var result = idempotencyService.validate("", TEST_PAYLOAD);

        // then
        assertThat(result.isCached()).isFalse();
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("응답 캐시 저장 - 성공")
    void 응답_캐시_저장_성공() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Object response = "response-data";

        // when
        idempotencyService.cacheResponse(TEST_IDEMPOTENCY_KEY, response);

        // then
        verify(valueOperations).set("idempotency:test-key-123", response, Duration.ofHours(24));
    }

    @Test
    @DisplayName("응답 캐시 저장 - null 키는 무시")
    void 응답_캐시_저장_null_키_무시() {
        // when
        idempotencyService.cacheResponse(null, "response");

        // then
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("처리 중 표시 해제 - 키 없으면 무시")
    void 처리_중_표시_해제_키_없으면_무시() {
        // when
        idempotencyService.unmarkAsProcessing(TEST_IDEMPOTENCY_KEY, null);

        // then
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("처리 중 표시 해제 - 키 있으면 삭제")
    void 처리_중_표시_해제_키_있으면_삭제() {
        // given
        when(redisTemplate.execute(any(DefaultRedisScript.class), anyList(), any())).thenReturn(1L);

        // when
        idempotencyService.unmarkAsProcessing(TEST_IDEMPOTENCY_KEY, "owner-token");

        // then
        verify(redisTemplate).execute(any(DefaultRedisScript.class),
                eq(java.util.List.of("processing:test-key-123")), eq("owner-token"));
    }

    @Test
    @DisplayName("처리 중 표시 해제 - null 키는 무시")
    void 처리_중_표시_해제_null_키_무시() {
        // when
        idempotencyService.unmarkAsProcessing(null, "owner-token");

        // then
        verifyNoInteractions(redisTemplate);
    }

    @Test
    @DisplayName("중복 요청 - 캐시된 응답 반환")
    void 중복_요청_캐시된_응답_반환() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Object cachedResponse = "cached-response";
        
        // 호출 순서: 1. Payload 확인 (null), 2. 캐시된 응답 확인 (있음)
        when(valueOperations.get(anyString()))
                .thenReturn(null)  // 첫 번째: idempotency:key:payload (null)
                .thenReturn(cachedResponse);  // 두 번째: idempotency:key (캐시된 응답)

        // when
        var result = idempotencyService.validate(TEST_IDEMPOTENCY_KEY, TEST_PAYLOAD);

        // then
        assertThat(result.isCached()).isTrue();
        assertThat(result.getCachedResponse()).isEqualTo(cachedResponse);
    }

    @Test
    @DisplayName("다른 Payload - 422 에러 발생")
    void 다른_Payload_422_에러_발생() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        // 첫 번째 호출: Payload 확인 - 다른 값 반환
        when(valueOperations.get(anyString())).thenReturn("different-payload");

        // when & then
        assertThatThrownBy(() -> idempotencyService.validate(TEST_IDEMPOTENCY_KEY, TEST_PAYLOAD))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException be = (BusinessException) exception;
                    assertThat(be.getCode()).isEqualTo(Code.IDEMPOTENCY_PAYLOAD_MISMATCH);
                });
    }

    @Test
    @DisplayName("처리 중인 요청 - 409 에러 발생")
    void 처리_중인_요청_409_에러_발생() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        // SETNX 실패 (false 반환 = 이미 처리 중)
        when(redisTemplate.opsForValue().setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> idempotencyService.validate(TEST_IDEMPOTENCY_KEY, TEST_PAYLOAD))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException be = (BusinessException) exception;
                    assertThat(be.getCode()).isEqualTo(Code.IDEMPOTENCY_CONFLICT);
                });
    }


    @Test
    @DisplayName("처리 중 표시 설정 - 이미 처리 중이면 실패")
    void 처리_중_표시_설정_이미_처리_중이면_실패() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        // SETNX 실패 (false 반환 = 이미 설정됨)
        when(redisTemplate.opsForValue().setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(false);

        // when & then
        assertThatThrownBy(() -> idempotencyService.validate(TEST_IDEMPOTENCY_KEY, TEST_PAYLOAD))
                .isInstanceOf(BusinessException.class)
                .satisfies(exception -> {
                    BusinessException be = (BusinessException) exception;
                    assertThat(be.getCode()).isEqualTo(Code.IDEMPOTENCY_CONFLICT);
                });
    }

    @Test
    @DisplayName("네임스페이스 포함 멱등성 키 - 정상 처리")
    void 네임스페이스_포함_멱등성_키_정상_처리() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redisTemplate.opsForValue().setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);

        // when
        var result = idempotencyService.validate(TEST_NAMESPACED_KEY, TEST_PAYLOAD);

        // then
        assertThat(result.isProcessing()).isTrue();
        assertThat(result.getProcessingToken()).isNotBlank();
        verify(redisTemplate.opsForValue()).setIfAbsent(
                eq("processing:userId123:test-key-123"),
                eq(result.getProcessingToken()), eq(Duration.ofMinutes(5)));
    }

    @Test
    @DisplayName("네임스페이스 포함 멱등성 키 - 캐시된 응답 반환")
    void 네임스페이스_포함_멱등성_키_캐시된_응답_반환() {
        // given
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        Object cachedResponse = "cached-response";
        
        when(valueOperations.get(anyString()))
                .thenReturn(null)  // 첫 번째: idempotency:userId123:test-key-123:payload (null)
                .thenReturn(cachedResponse);  // 두 번째: idempotency:userId123:test-key-123 (캐시된 응답)

        // when
        var result = idempotencyService.validate(TEST_NAMESPACED_KEY, TEST_PAYLOAD);

        // then
        assertThat(result.isCached()).isTrue();
        assertThat(result.getCachedResponse()).isEqualTo(cachedResponse);
    }

    @Test
    @DisplayName("다른 사용자의 동일한 멱등성 키 - 충돌 없음")
    void 다른_사용자의_동일한_멱등성_키_충돌_없음() {
        // given
        String anotherUserKey = "userId456:test-key-123";
        when(redisTemplate.opsForValue()).thenReturn(valueOperations);
        when(valueOperations.get(anyString())).thenReturn(null);
        when(redisTemplate.opsForValue().setIfAbsent(anyString(), any(), any(Duration.class))).thenReturn(true);

        // when - 첫 번째 사용자
        var result1 = idempotencyService.validate(TEST_NAMESPACED_KEY, TEST_PAYLOAD);
        
        // when - 두 번째 사용자
        var result2 = idempotencyService.validate(anotherUserKey, TEST_PAYLOAD);

        // then - 두 요청 모두 성공
        assertThat(result1.isProcessing()).isTrue();
        assertThat(result2.isProcessing()).isTrue();
        // 서로 다른 키로 저장됨
        verify(redisTemplate.opsForValue()).setIfAbsent(eq("processing:userId123:test-key-123"), anyString(), any());
        verify(redisTemplate.opsForValue()).setIfAbsent(eq("processing:userId456:test-key-123"), anyString(), any());
    }
}
