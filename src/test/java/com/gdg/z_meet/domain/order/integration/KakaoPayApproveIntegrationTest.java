package com.gdg.z_meet.domain.order.integration;

import com.gdg.z_meet.domain.order.client.KaKaoPayApiClient;
import com.gdg.z_meet.domain.order.dto.KaKaoPayApproveDTO;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.entity.enums.ProductType;
import com.gdg.z_meet.domain.order.repository.KakaoItemPurchaseRepository;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.domain.order.service.approve.KakaoPayApproveService;
import com.gdg.z_meet.domain.order.service.idempotency.KakaoPayIdempotencyService;
import com.gdg.z_meet.domain.order.service.locking.KakaoPayLockService;
import com.gdg.z_meet.domain.order.service.monitoring.KakaoPayLockMonitoringService;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.entity.enums.*;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
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

import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.sql.Statement;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
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

        @Autowired // Changed from @MockBean to @Autowired to test actual locking mechanism
        private KakaoPayLockService kakaoPayLockService;

        @Autowired
        @Qualifier("lockDataSource")
        private DataSource lockDataSource;

        @MockBean
        private KaKaoPayApiClient kaKaoPayApiClient;

        @MockBean
        private KakaoPayIdempotencyService kakaoPayIdempotencyService;

        @MockBean
        private KakaoPayLockMonitoringService kakaoPayLockMonitoringService;

        @MockBean
        private RedisTemplate<String, Object> redisTemplate;

        // External dependencies mocks
        @MockBean
        private ConnectionFactory connectionFactory;
        @MockBean
        private RabbitTemplate rabbitTemplate;
        @MockBean
        private MongoTemplate mongoTemplate;
        @MockBean
        private MessageRepository messageRepository;
        @MockBean
        private RedisMessageListenerContainer redisMessageListenerContainer;

        private User buyer;

        @BeforeEach
        void setUp() {
                // Register H2 aliases for MySQL Named Lock functions
                try (Connection conn = lockDataSource.getConnection();
                                Statement stmt = conn.createStatement()) {
                        stmt.execute("CREATE ALIAS IF NOT EXISTS GET_LOCK FOR \"com.gdg.z_meet.global.util.H2LockUtils.getLock\"");
                        stmt.execute("CREATE ALIAS IF NOT EXISTS RELEASE_LOCK FOR \"com.gdg.z_meet.global.util.H2LockUtils.releaseLock\"");
                } catch (SQLException e) {
                        throw new RuntimeException("Failed to register H2 aliases", e);
                }

                // Mock RedisTemplate
                org.mockito.Mockito.when(redisTemplate.hasKey(anyString())).thenReturn(false);
                ValueOperations<String, Object> valueOps = org.mockito.Mockito.mock(ValueOperations.class);
                org.mockito.Mockito.when(redisTemplate.opsForValue()).thenReturn(valueOps);
                org.mockito.Mockito.doNothing().when(valueOps).set(anyString(), any());

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
                return kakaoPayDataRepository.saveAndFlush(data);
        }

        private KaKaoPayApproveDTO.KaKaoApiResponse mockApproveApiResponse(String orderId, String tid, long total,
                        long vat) {
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
                long total = 1200L;
                prepareKakaoPayData(orderId, tid, total, ProductType.TICKET);

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
                approveService.approve(param, null);

                // then
                KakaoPayData updated = kakaoPayDataRepository.findByOrderId(orderId).orElseThrow();
                assertThat(updated.getStatus()).isEqualTo(PaymentStatus.APPROVED);
                assertThat(updated.getItemPurchase()).isNotNull();
                assertThat(itemPurchaseRepository.existsByOrderId(orderId)).isTrue();

                verify(kakaoPayLockMonitoringService, times(1))
                                .acquired(argThat(lockName -> lockName.contains(orderId)), anyString(),
                                                any(java.time.Instant.class), anyInt());
                verify(kakaoPayLockMonitoringService, times(1))
                                .released(argThat(lockName -> lockName.contains(orderId)), anyString(),
                                                any(java.time.Instant.class), anyInt());
        }
}
