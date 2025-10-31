package com.gdg.z_meet.domain.order.integration;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.PaymentStatus;
import com.gdg.z_meet.domain.order.entity.ProductType;
import com.gdg.z_meet.domain.order.repository.KakaoItemPurchaseRepository;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.domain.order.service.KakaoPayApproveService;
import com.gdg.z_meet.domain.order.service.KakaoPayIdempotencyService;
import com.gdg.z_meet.domain.order.service.KakaoPayLockMonitoringService;
import com.gdg.z_meet.domain.order.service.KakaoPayLockService;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.entity.enums.*;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.mock.mockito.MockBean;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.core.ValueOperations;
import org.springframework.data.mongodb.core.MongoTemplate;
import com.gdg.z_meet.domain.chat.repository.mongo.MessageRepository;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Optional;
import java.util.concurrent.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class KakaoPayApproveIntegrationTest {

    @Autowired
    private KakaoPayApproveService approveService;

    @Autowired
    private KakaoPayDataRepository kakaoPayDataRepository;

    @Autowired
    private KakaoItemPurchaseRepository itemPurchaseRepository;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private UserProfileRepository userProfileRepository;

    @Autowired
    private EntityManager entityManager;

    @MockBean
    private KakaoPayLockService kakaoPayLockService;

    @MockBean
    private KaKaoPayApiClient kaKaoPayApiClient;

    @MockBean
    private KakaoPayIdempotencyService kakaoPayIdempotencyService;

    @MockBean
    private KakaoPayLockMonitoringService kakaoPayLockMonitoringService;

    // Provide mocks to satisfy external dependencies in test context
    @MockBean
    private ConnectionFactory connectionFactory;
    @MockBean
    private RabbitTemplate rabbitTemplate;
    @MockBean
    private RedisTemplate<String, Object> redisTemplate;
    @MockBean
    private MongoTemplate mongoTemplate;
    @MockBean
    private MessageRepository messageRepository;
    @MockBean
    private RedisMessageListenerContainer redisMessageListenerContainer;

    private User buyer;

    @BeforeEach
    void setUp() {
        // Mock RedisTemplate operations to prevent NPE in ChatRoomCommandService.initRandomChatIdRedis()
        org.mockito.Mockito.when(redisTemplate.hasKey(org.mockito.ArgumentMatchers.anyString()))
                .thenReturn(false);
        ValueOperations<String, Object> valueOps = org.mockito.Mockito.mock(ValueOperations.class);
        org.mockito.Mockito.when(redisTemplate.opsForValue())
                .thenReturn(valueOps);
        org.mockito.Mockito.doNothing().when(valueOps).set(org.mockito.ArgumentMatchers.anyString(), org.mockito.ArgumentMatchers.any());
        
        String uniqueId = String.valueOf(System.currentTimeMillis());
        buyer = userRepository.saveAndFlush(User.builder()
                .studentNumber("20250001" + uniqueId)
                .password("pw")
                .name("buyer")
                .phoneNumber("01012345678" + uniqueId.substring(uniqueId.length() - 4))
                .build());

        UserProfile userProfile = UserProfile.builder()
                .nickname("testuser" + uniqueId)
                .emoji("😀")
                .music(Music.KPOP)
                .mbti(MBTI.ENFP)
                .style(Style.CASUAL)
                .idealType(IdealType.DOG)
                .idealAge(IdealAge.SAME)
                .gender(Gender.MALE)
                .grade(Grade.THIRD)
                .major(Major.CSIE)
                .age(23)
                .user(buyer)
                .build();
        userProfileRepository.saveAndFlush(userProfile);
    }

    private KakaoPayData prepareKakaoPayData(String orderId, String tid, long totalPrice, ProductType productType) {
        KakaoPayData data = KakaoPayData.builder()
                .orderId(orderId)
                .tid(tid)
                .buyer(buyer)
                .totalPrice(totalPrice)
                .productType(productType)
                .status(PaymentStatus.PREPARED)
                .build();
        return kakaoPayDataRepository.save(data);
    }

    private KaKaoPayApproveDTO.KaKaoApiResponse mockApproveApiResponse(String orderId, String tid, long total, long vat) {
        return KaKaoPayApproveDTO.KaKaoApiResponse.builder()
                .aid("aid-1")
                .tid(tid)
                .cid("TC0ONETIME")
                .partner_order_id(orderId)
                .partner_user_id(String.valueOf(buyer.getId()))
                .product_type("TICKET")
                .amount(KaKaoPayApproveDTO.Amount.builder().total(total).vat(vat).build())
                .approved_at("2025-01-01T00:00:00")
                .build();
    }

    @Test
    void 결제승인_성공_상태변경_내역생성() {
        // given
        String orderId = "order-approve-success";
        String tid = "TID-OK-1";
        long total = 1200L; // TICKET 유효 가격: 500, 1200, 3000
        prepareKakaoPayData(orderId, tid, total, ProductType.TICKET);

        given(kakaoPayLockService.acquireLock(eq(orderId)))
                .willReturn("LOCK_KAKAO_PAY_APPROVE_" + orderId);
        given(kaKaoPayApiClient.requestPaymentApprove(any(), any()))
                .willReturn(Optional.of(mockApproveApiResponse(orderId, tid, total, 120L)));
        given(kakaoPayIdempotencyService.validate(nullable(String.class), any(String.class)))
                .willReturn(KakaoPayIdempotencyService.IdempotencyValidationResult.processing());

        KaKaoPayApproveDTO.Parameter param = KaKaoPayApproveDTO.Parameter.builder()
                .userId(buyer.getId())
                .orderId(orderId)
                .pgToken("pg-token")
                .build();

        // when
        var res = approveService.approve(param, null);

        // then
        KakaoPayData updated = kakaoPayDataRepository.findByOrderId(orderId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(updated.getItemPurchase()).isNotNull();
        assertThat(itemPurchaseRepository.existsByOrderId(orderId)).isTrue();

        verify(kakaoPayLockService, times(1))
                .acquireLock(eq(orderId));
    }

    @Test
    void 결제승인_동시성_하나성공_하나충돌() throws Exception {
        // given
        String orderId = "order-concurrent";
        String tid = "TID-OK-2";
        long total = 3000L; // TICKET 유효 가격: 500, 1200, 3000
        prepareKakaoPayData(orderId, tid, total, ProductType.TICKET);

        // first call acquires, second times out
        given(kakaoPayLockService.acquireLock(eq(orderId)))
                .willReturn("LOCK_KAKAO_PAY_APPROVE_" + orderId) // first thread
                .willThrow(new BusinessException(com.gdg.z_meet.global.response.Code.IDEMPOTENCY_CONFLICT)); // second thread
        given(kaKaoPayApiClient.requestPaymentApprove(any(), any()))
                .willReturn(Optional.of(mockApproveApiResponse(orderId, tid, total, 300L)));
        given(kakaoPayIdempotencyService.validate(nullable(String.class), any(String.class)))
                .willReturn(KakaoPayIdempotencyService.IdempotencyValidationResult.processing());

        KaKaoPayApproveDTO.Parameter param = KaKaoPayApproveDTO.Parameter.builder()
                .userId(buyer.getId())
                .orderId(orderId)
                .pgToken("pg-token")
                .build();

        ExecutorService es = Executors.newFixedThreadPool(2);
        Callable<Object> task = () -> approveService.approve(param, null);

        Future<Object> f1 = es.submit(task);
        Future<Object> f2 = es.submit(task);

        int success = 0;
        int conflict = 0;
        for (Future<Object> f : new Future[]{f1, f2}) {
            try {
                f.get(5, TimeUnit.SECONDS);
                success++;
            } catch (ExecutionException ee) {
                if (ee.getCause() instanceof BusinessException) {
                    conflict++;
                } else {
                    throw ee;
                }
            }
        }

        es.shutdownNow();

        assertThat(success).isEqualTo(1);
        assertThat(conflict).isEqualTo(1);

        KakaoPayData updated = kakaoPayDataRepository.findByOrderId(orderId).orElseThrow();
        assertThat(updated.getStatus()).isEqualTo(PaymentStatus.APPROVED);
        assertThat(itemPurchaseRepository.existsByOrderId(orderId)).isTrue();
    }
}


