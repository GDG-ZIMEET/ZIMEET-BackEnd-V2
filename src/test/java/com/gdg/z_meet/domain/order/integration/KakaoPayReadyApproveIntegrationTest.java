package com.gdg.z_meet.domain.order.integration;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.dto.KaKaoPayReadyDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.PaymentStatus;
import com.gdg.z_meet.domain.order.entity.ProductType;
import com.gdg.z_meet.domain.order.repository.KakaoItemPurchaseRepository;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.domain.order.repository.NamedLockRepository;
import com.gdg.z_meet.domain.order.service.KakaoPayApproveService;
import com.gdg.z_meet.domain.order.service.KakaoPayIdempotencyService;
import com.gdg.z_meet.domain.order.service.KakaoPayLockMonitoringService;
import com.gdg.z_meet.domain.order.service.KakaoPayReadyService;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.entity.enums.*;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import com.gdg.z_meet.domain.user.repository.UserRepository;
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
import org.springframework.data.mongodb.core.MongoTemplate;
import com.gdg.z_meet.domain.chat.repository.mongo.MessageRepository;
import org.springframework.data.redis.listener.RedisMessageListenerContainer;

import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.BDDMockito.given;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;

@SpringBootTest
@ActiveProfiles("test")
class KakaoPayReadyApproveIntegrationTest {

        @Autowired
        private KakaoPayReadyService readyService;
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

        @MockBean
        private NamedLockRepository namedLockRepository;
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
                // Mock RedisTemplate operations to prevent NPE in
                // ChatRoomCommandService.initRandomChatIdRedis()
                org.mockito.Mockito.when(redisTemplate.hasKey(org.mockito.ArgumentMatchers.anyString()))
                                .thenReturn(false);
                org.springframework.data.redis.core.ValueOperations<String, Object> valueOps = org.mockito.Mockito
                                .mock(org.springframework.data.redis.core.ValueOperations.class);
                org.mockito.Mockito.when(redisTemplate.opsForValue())
                                .thenReturn(valueOps);
                org.mockito.Mockito.doNothing().when(valueOps).set(org.mockito.ArgumentMatchers.anyString(),
                                org.mockito.ArgumentMatchers.any());

                String uniqueId = String.valueOf(System.currentTimeMillis());
                buyer = userRepository.saveAndFlush(User.builder()
                                .studentNumber("20250002" + uniqueId)
                                .password("pw")
                                .name("buyer2")
                                .phoneNumber("01011112222" + uniqueId.substring(uniqueId.length() - 4))
                                .build());

                UserProfile userProfile = UserProfile.builder()
                                .nickname("testuser2" + uniqueId)
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

        @Test
        void 결제_준비에서_승인까지_네임드락_검증() {
                // given - ready
                long total = 1200L; // TICKET 유효 가격: 500, 1200, 3000
                KaKaoPayReadyDTO.Parameter readyParam = KaKaoPayReadyDTO.Parameter.builder()
                                .buyerId(buyer.getId())
                                .productType(ProductType.TICKET.name())
                                .totalPrice(total)
                                .vat(120L)
                                .build();

                String tid = "TID-READY-OK";
                KaKaoPayReadyDTO.KakaoApiResponse readyApiRes = KaKaoPayReadyDTO.KakaoApiResponse.builder()
                                .tid(tid)
                                .next_redirect_pc_url("http://pay/redirect")
                                .build();

                given(kaKaoPayApiClient.requestPaymentReady(any(), anyString(), any()))
                                .willReturn(Optional.of(readyApiRes));
                given(kakaoPayIdempotencyService.validate(nullable(String.class), any(String.class)))
                                .willReturn(KakaoPayIdempotencyService.IdempotencyValidationResult.processing());

                // when - ready
                KaKaoPayReadyDTO.Response readyRes = readyService.ready(readyParam, null);

                // then - saved prepared data
                KakaoPayData saved = kakaoPayDataRepository.findByOrderId(readyRes.getOrderId()).orElseThrow();
                assertThat(saved.getStatus()).isEqualTo(PaymentStatus.PREPARED);
                assertThat(saved.getTid()).isEqualTo(tid);

                // given - approve
                // NamedLockRepository mock: 락 획득 성공
                given(namedLockRepository.getLock(anyString(), anyInt()))
                                .willReturn(1);

                KaKaoPayApproveDTO.KaKaoApiResponse approveApiRes = KaKaoPayApproveDTO.KaKaoApiResponse.builder()
                                .aid("aid-2")
                                .tid(tid)
                                .cid("TC0ONETIME")
                                .partner_order_id(readyRes.getOrderId())
                                .partner_user_id(String.valueOf(buyer.getId()))
                                .product_type("TICKET")
                                .amount(KaKaoPayApproveDTO.Amount.builder().total(total).vat(120L).build())
                                .approved_at("2025-01-02T00:00:00")
                                .build();

                given(kaKaoPayApiClient.requestPaymentApprove(any(), any()))
                                .willReturn(Optional.of(approveApiRes));

                KaKaoPayApproveDTO.Parameter approveParam = KaKaoPayApproveDTO.Parameter.builder()
                                .userId(buyer.getId())
                                .orderId(readyRes.getOrderId())
                                .pgToken("pg-token-2")
                                .build();

                // when - approve
                approveService.approve(approveParam, null);

                // then - approved and purchase created, and named lock used
                KakaoPayData updated = kakaoPayDataRepository.findByOrderId(readyRes.getOrderId()).orElseThrow();
                assertThat(updated.getStatus()).isEqualTo(PaymentStatus.APPROVED);
                assertThat(itemPurchaseRepository.existsByOrderId(readyRes.getOrderId())).isTrue();

                // Verify named lock was acquired
                String expectedLockName = "LOCK_KAKAO_PAY_APPROVE_" + readyRes.getOrderId();
                verify(namedLockRepository, times(1))
                                .getLock(argThat(lockName -> lockName.contains(readyRes.getOrderId())), anyInt());
                verify(namedLockRepository, times(1))
                                .releaseLock(argThat(lockName -> lockName.contains(readyRes.getOrderId())));

                // Verify lock monitoring events were recorded
                // acquired: 락 획득 성공 시 호출
                verify(kakaoPayLockMonitoringService, times(1))
                                .acquired(argThat(lockName -> lockName.contains(readyRes.getOrderId())), anyString(),
                                                any(java.time.Instant.class), anyInt());
                // released: 트랜잭션 커밋 후 afterCommit에서 호출
                verify(kakaoPayLockMonitoringService, times(1))
                                .released(argThat(lockName -> lockName.contains(readyRes.getOrderId())), anyString(),
                                                any(java.time.Instant.class), anyInt());
        }
}
