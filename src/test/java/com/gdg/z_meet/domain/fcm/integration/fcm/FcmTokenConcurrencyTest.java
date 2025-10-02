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
        fcmTokenRepository.deleteAll();
        userRepository.deleteAll();
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동시에 여러 요청이 들어와도 FcmToken은 1개만 생성된다")
    void 동시_요청시_토큰_중복_생성_방지() throws InterruptedException {
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                            .fcmToken("concurrent-token-" + index)
                            .build();

                    fcmTokenService.syncFcmToken(testUser.getId(), req);
                    successCount.incrementAndGet();
                } catch (Exception e) {
                    System.err.println("Thread " + index + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // DB에서 다시 조회 (1개만 존재해야 함)
        List<FcmToken> tokens = fcmTokenRepository.findAllByUser(testUser);

        assertThat(tokens).hasSize(1);
    }


    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("동시에 같은 토큰으로 요청해도 중복 생성되지 않는다")
    void 동시_요청_같은_토큰_중복_방지() throws InterruptedException {
        int threadCount = 5;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        String sameToken = "same-token-value";

        for (int i = 0; i < threadCount; i++) {
            executorService.submit(() -> {
                try {
                    UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                            .fcmToken(sameToken)
                            .build();

                    fcmTokenService.syncFcmToken(testUser.getId(), req);
                } catch (Exception e) {
                    System.err.println("Failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        List<FcmToken> tokens = fcmTokenRepository.findAllByUser(testUser);

        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getToken()).isEqualTo(sameToken);
    }

    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("기존 토큰이 있을 때 동시 업데이트 요청이 와도 1개만 유지된다")
    void 기존_토큰_존재시_동시_업데이트_중복_방지() throws InterruptedException {
        FcmToken existingToken = FcmToken.builder()
                .user(testUser)
                .token("existing-token")
                .build();
        fcmTokenRepository.save(existingToken);

        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);

        for (int i = 0; i < threadCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                            .fcmToken("updated-token-" + index)
                            .build();

                    fcmTokenService.syncFcmToken(testUser.getId(), req);
                } catch (Exception e) {
                    System.err.println("Thread " + index + " failed: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        List<FcmToken> tokens = fcmTokenRepository.findAllByUser(testUser);

        assertThat(tokens).hasSize(1);
        assertThat(tokens.get(0).getToken()).startsWith("updated-token-");
    }
}
