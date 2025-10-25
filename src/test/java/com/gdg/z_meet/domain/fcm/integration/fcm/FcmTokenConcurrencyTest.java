package com.gdg.z_meet.domain.fcm.integration.fcm;

import com.gdg.z_meet.domain.fcm.entity.FcmToken;
import com.gdg.z_meet.domain.fcm.repository.FcmTokenRepository;
import com.gdg.z_meet.domain.fcm.service.token.FcmTokenService;
import com.gdg.z_meet.domain.fcm.service.token.FcmTokenServiceImpl;
import com.gdg.z_meet.domain.fcm.unit.config.QueryDslTestConfig;
import com.gdg.z_meet.domain.user.dto.UserReq;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import jakarta.persistence.EntityManager;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.jdbc.AutoConfigureTestDatabase;
import org.springframework.boot.test.autoconfigure.orm.jpa.DataJpaTest;
import org.springframework.context.annotation.Import;
import org.springframework.test.annotation.Rollback;
import org.springframework.test.context.ActiveProfiles;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({FcmTokenServiceImpl.class, QueryDslTestConfig.class})
@Rollback(value = false)
@DisplayName("FCM 토큰 동시성 통합 테스트")
class FcmTokenConcurrencyTest {

    @Autowired
    private FcmTokenService fcmTokenService;

    @Autowired
    private UserRepository userRepository;

    @Autowired
    private FcmTokenRepository fcmTokenRepository;

    @Autowired
    private EntityManager em;

    private User testUser;

    @BeforeEach
    void setUp() {
        testUser = User.builder()
                .studentNumber("20192098")
                .name("동시성테스트")
                .phoneNumber("010-1111-2222")
                .password("password")
                .pushAgree(true)
                .build();
        // DB에 즉시 반영 + 영속성 컨텍스트 비우기
        testUser = userRepository.saveAndFlush(testUser);
        em.clear();
    }

    @AfterEach
    void tearDown() {
        // FCM 토큰 먼저 삭제 (외래키 제약조건 때문에)
        fcmTokenRepository.deleteAll();
        // 사용자 삭제
        userRepository.deleteAll();
        // 영속성 컨텍스트 비우기
        em.clear();
    }


    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("CASE 1: 기존에 토큰이 있을 때 여러 요청이 동시에 update 시도")
    void CASE1_기존_토큰_있음_동시_UPDATE_시도() throws InterruptedException {
        // DB에 이미 user_id의 토큰 "old-token" 존재
        String oldToken = "old-token";
        FcmToken existingToken = FcmToken.builder()
                .user(testUser)
                .token(oldToken)
                .build();
        fcmTokenRepository.save(existingToken);

        // 기존 토큰 존재 확인
        List<FcmToken> beforeTokens = fcmTokenRepository.findAllByUser(testUser);
        assertThat(beforeTokens).hasSize(1);
        assertThat(beforeTokens.get(0).getToken()).isEqualTo(oldToken);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // 10개 스레드가 "updated-token-0" ~ "updated-token-9"로 동시에 요청
        for (int i = 0; i < threadCount; i++) {
            final int tokenIndex = i;
            executorService.submit(() -> {
                try {
                    String newToken = "updated-token-" + tokenIndex;
                    UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                            .fcmToken(newToken)
                            .build();

                    fcmTokenService.syncFcmToken(testUser.getId(), req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.err.println("Thread " + tokenIndex + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        // 검증: DB에는 여전히 row 1개만 존재
        List<FcmToken> afterTokens = fcmTokenRepository.findAllByUser(testUser);

        System.out.println("=== CASE 1 결과 ===");
        System.out.println("성공한 요청: " + successCount.get());
        System.out.println("실패한 요청: " + failureCount.get());
        System.out.println("최종 토큰 개수: " + afterTokens.size());
        System.out.println("최종 토큰 값: " + afterTokens.get(0).getToken());

        // 검증 포인트
        assertThat(afterTokens).hasSize(1); // row 개수 유지 (COUNT(*) = 1)
        assertThat(successCount.get()).isEqualTo(threadCount); // 예외/충돌 없이 모든 요청 정상 커밋
        assertThat(failureCount.get()).isEqualTo(0); // Deadlock 없이 정상 종료
        assertThat(afterTokens.get(0).getToken()).startsWith("updated-token-"); // 최종 token 값은 요청 중 하나의 값
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("CASE 2: 기존에 토큰이 없는 상태에서 여러 요청이 동시에 insert 시도")
    void CASE1_기존_토큰_없음_동시_INSERT_시도() throws InterruptedException {
        // DB에 user_id의 FcmToken row가 없는 상태 확인
        List<FcmToken> beforeTokens = fcmTokenRepository.findAllByUser(testUser);
        assertThat(beforeTokens).isEmpty();

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // 동시에 10개의 스레드가 서로 다른 토큰 값으로 요청
        for (int i = 0; i < threadCount; i++) {
            final int tokenIndex = i;
            executorService.submit(() -> {
                try {
                    UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                            .fcmToken("token-" + tokenIndex)
                            .build();

                    fcmTokenService.syncFcmToken(testUser.getId(), req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    failureCount.incrementAndGet();
                    System.err.println("Thread " + tokenIndex + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(15, TimeUnit.SECONDS);
        executorService.shutdown();

        // 검증: DB에는 최종적으로 1개의 row만 존재
        List<FcmToken> afterTokens = fcmTokenRepository.findAllByUser(testUser);

        System.out.println("=== CASE 2 결과 ===");
        System.out.println("성공한 요청: " + successCount.get());
        System.out.println("실패한 요청: " + failureCount.get());
        System.out.println("최종 토큰 개수: " + afterTokens.size());
        System.out.println("최종 토큰 값: " + (afterTokens.isEmpty() ? "없음" : afterTokens.get(0).getToken()));

        // 검증 포인트
        assertThat(afterTokens).hasSize(1); // DB에는 1개의 row만 존재
        assertThat(successCount.get()).isGreaterThan(0); // 최소 1개는 성공
        // UNIQUE 에러는 발생할 수 있지만, 최종적으로 1개만 존재하면 OK
    }
}
