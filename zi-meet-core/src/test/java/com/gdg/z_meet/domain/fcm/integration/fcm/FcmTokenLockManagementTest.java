package com.gdg.z_meet.domain.fcm.integration.fcm;

import com.gdg.z_meet.domain.fcm.entity.FcmToken;
import com.gdg.z_meet.domain.fcm.repository.FcmTokenRepository;
import com.gdg.z_meet.domain.fcm.service.token.FcmTokenService;
import com.gdg.z_meet.domain.fcm.service.token.FcmTokenServiceImpl;
import com.gdg.z_meet.domain.fcm.service.token.FcmTokenTransactionService;
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

/**
 * ReentrantLock 도입이 분할 락(split lock) 문제를 해결했는지 검증
 * 
 * 검증 포인트:
 * 1. Split Lock 해결: 동일 userId에 대해 동일 락 객체 사용 보장
 * 2. 락 관리: 조건부 제거가 올바르게 동작하는지
 * 3. 메모리 안전성: 락 객체가 적절히 정리되는지
 * 4. 동시성 안전성: UNIQUE 제약조건 위반 없이 정상 동작
 */
@DataJpaTest
@AutoConfigureTestDatabase(replace = AutoConfigureTestDatabase.Replace.NONE)
@ActiveProfiles("test")
@Import({FcmTokenServiceImpl.class, FcmTokenTransactionService.class, QueryDslTestConfig.class})
@Rollback(value = false)
@DisplayName("ReentrantLock 분할 락 해결 검증 테스트")
class FcmTokenLockManagementTest {

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
                .studentNumber("2024" + System.currentTimeMillis()) // 중복 방지
                .name("락관리테스트")
                .phoneNumber("010-" + System.currentTimeMillis())
                .password("password")
                .pushAgree(true)
                .build();
        testUser = userRepository.saveAndFlush(testUser);
        em.clear();
    }

    @AfterEach
    void tearDown() {
        fcmTokenRepository.deleteAll();
        userRepository.deleteAll();
        em.clear();
    }

    /**
     * 검증 1: 분할 락(split lock) 해결 확인
     * 
     * ReentrantLock 구현의 핵심:
     * - 조건부 제거(hasQueuedThreads 체크)로 동일 락 객체 보장
     * - synchronized(Object)는 finally에서 즉시 remove 시 다른 락 객체 발생 가능
     * 
     * 검증 방법: UNIQUE 제약조건 위반 없이 모든 요청이 성공하는지 확인
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("검증 1: 분할 락 해결 - 동시 요청 시 UNIQUE 제약조건 위반 없음")
    void 검증1_Synchronized_해결_UNIQUE제약조건위반없음() throws InterruptedException {
        int threadCount = 20;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);
        AtomicInteger failureCount = new AtomicInteger(0);

        // 동시에 요청
        for (int i = 0; i < threadCount; i++) {
            final int tokenIndex = i;
            executorService.submit(() -> {
                try {
                    UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                            .fcmToken("split-lock-test-token-" + tokenIndex)
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

        // 검증: DB에는 1개만 존재 (분할 락 발생 시 여러 개 생성됨)
        List<FcmToken> afterTokens = fcmTokenRepository.findAllByUser(testUser);

        System.out.println("=== 검증 1: Synchronized 개선 ===");
        System.out.println("성공: " + successCount.get() + ", 실패: " + failureCount.get());
        System.out.println("최종 토큰 개수: " + afterTokens.size());

        // 핵심 검증: 분할 락이 발생하지 않았는지
        assertThat(afterTokens).hasSize(1); // 1개만 존재
        assertThat(successCount.get()).isEqualTo(threadCount); // 모두 성공
        assertThat(failureCount.get()).isEqualTo(0); // 실패 없음
    }

    /**
     * 검증 2: 락 해제 및 관리 로직 확인
     * 
     * 조건부 제거 로직:
     * if(!lock.isLocked() && !lock.hasQueuedThreads()) {
     *     userLocks.remove(userId, lock);
     * }
     * 
     * 검증 방법: 연속 요청 시 정상 동작하는지 확인
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("검증 2: 락 관리 - 연속 요청 시 정상 동작")
    void 검증2_락관리_연속요청_정상동작() throws InterruptedException {
        // 첫 요청
        UserReq.saveFcmTokenReq req1 = UserReq.saveFcmTokenReq.builder()
                .fcmToken("test-token-1")
                .build();
        fcmTokenService.syncFcmToken(testUser.getId(), req1);
        
        // 첫 요청 후 상태 확인
        List<FcmToken> tokensAfterFirst = fcmTokenRepository.findAllByUser(testUser);
        assertThat(tokensAfterFirst).hasSize(1);
        
        // 여러 요청 동시 실행
        int threadCount = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(threadCount);
        CountDownLatch latch = new CountDownLatch(threadCount);
        AtomicInteger successCount = new AtomicInteger(0);

        for (int i = 0; i < threadCount; i++) {
            final int tokenIndex = i;
            executorService.submit(() -> {
                try {
                    UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                            .fcmToken("test-token-" + tokenIndex)
                            .build();
                    fcmTokenService.syncFcmToken(testUser.getId(), req);
                    successCount.incrementAndGet();
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(10, TimeUnit.SECONDS);
        executorService.shutdown();

        // 최종 검증: 정상 동작
        List<FcmToken> tokens = fcmTokenRepository.findAllByUser(testUser);
        System.out.println("=== 검증 2: 락 관리 ===");
        System.out.println("성공: " + successCount.get());
        System.out.println("최종 토큰 개수: " + tokens.size());
        
        assertThat(tokens).hasSize(1); // 1개만 존재
        assertThat(successCount.get()).isEqualTo(threadCount); // 모두 성공
    }

    /**
     * 검증 4: 여러 사용자 동시 요청 - 각 사용자별로 분리된 락 사용
     * 
     * 검증 포인트:
     * - 여러 사용자가 동시에 요청해도 각자의 락 사용
     * - 사용자 간 간섭 없음
     */
    @Test
    @Transactional(propagation = Propagation.NOT_SUPPORTED)
    @DisplayName("검증 4: 여러 사용자 동시 요청 - 사용자별 분리된 락")
    void 검증4_여러사용자_동시요청_분리된락() throws InterruptedException {
        // 여러 사용자 생성
        User[] users = new User[5];
        for (int i = 0; i < 5; i++) {
            users[i] = User.builder()
                    .studentNumber("202500" + i)
                    .name("멀티사용자" + i)
                    .phoneNumber("010-2222-000" + i)
                    .password("password")
                    .pushAgree(true)
                    .build();
            userRepository.saveAndFlush(users[i]);
        }

        int threadCountPerUser = 10;
        ExecutorService executorService = Executors.newFixedThreadPool(50);
        CountDownLatch latch = new CountDownLatch(users.length * threadCountPerUser);
        AtomicInteger successCount = new AtomicInteger(0);

        for (User user : users) {
            for (int i = 0; i < threadCountPerUser; i++) {
                final int tokenIndex = i;
                executorService.submit(() -> {
                    try {
                        UserReq.saveFcmTokenReq req = UserReq.saveFcmTokenReq.builder()
                                .fcmToken("multi-user-token-" + user.getId() + "-" + tokenIndex)
                                .build();
                        fcmTokenService.syncFcmToken(user.getId(), req);
                        successCount.incrementAndGet();
                    } finally {
                        latch.countDown();
                    }
                });
            }
        }

        latch.await(20, TimeUnit.SECONDS);
        executorService.shutdown();

        System.out.println("=== 검증 4: 여러 사용자 동시 요청 ===");
        System.out.println("성공 요청 수: " + successCount.get());

        // 각 사용자별로 1개의 토큰만 존재해야 함
        for (User user : users) {
            List<FcmToken> tokens = fcmTokenRepository.findAllByUser(user);
            assertThat(tokens).hasSize(1);
        }
    }
}

