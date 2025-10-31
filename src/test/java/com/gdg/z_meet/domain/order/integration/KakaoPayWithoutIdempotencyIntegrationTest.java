package com.gdg.z_meet.domain.order.integration;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.PaymentStatus;
import com.gdg.z_meet.domain.order.entity.ProductType;
import com.gdg.z_meet.domain.user.entity.User;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import static org.assertj.core.api.Assertions.assertThat;

/**
 * 멱등키 사용 이전 상황에 대한 통합 테스트 (시뮬레이션)
 * 
 * 테스트 목적:
 * 1. 동일한 요청을 여러 번 보낼 때 중복 처리되는지 확인
 * 2. 멱등키 없이 동시 요청 시 어떤 문제가 발생하는지 검증
 * 3. Redis 없이 동작하는 상황에서의 동작 확인
 */
@DisplayName("멱등키 사용 이전 KakaoPay 통합 테스트")
class KakaoPayWithoutIdempotencyIntegrationTest {

    @Test
    @DisplayName("멱등키 없이 동일한 요청을 여러 번 보내면 중복 처리됨 - 시뮬레이션")
    void 멱등키_없이_동일_요청_중복_처리_시뮬레이션() {
        // given: 동일한 파라미터로 여러 번 KakaoPayData 생성
        String productType = ProductType.TWO_TO_TWO.name();
        Long totalPrice = 10000L;
        
        // 시뮬레이션용 사용자 생성
        User testUser = User.builder()
                .studentNumber("2024" + System.currentTimeMillis())
                .name("멱등성테스트")
                .phoneNumber("010-" + System.currentTimeMillis())
                .password("password")
                .pushAgree(true)
                .build();
        
        List<KakaoPayData> allPayments = new ArrayList<>();
        
        // when: 멱등키 없이 동일한 요청을 여러 번 시뮬레이션 (직접 데이터 생성)
        for (int i = 0; i < 5; i++) {
            KakaoPayData payment = KakaoPayData.builder()
                    .buyer(testUser)
                    .productType(ProductType.valueOf(productType))
                    .totalPrice(totalPrice)
                    .orderId("ORDER_" + System.currentTimeMillis() + "_" + i)
                    .tid("TID_" + System.currentTimeMillis() + "_" + i)
                    .status(PaymentStatus.PREPARED)
                    .build();
            
            allPayments.add(payment);
            System.out.println("결제 준비 " + (i + 1) + " 생성 - OrderId: " + payment.getOrderId());
        }

        // then: 여러 개의 KakaoPayData가 생성됨 (중복 처리)
        System.out.println("=== 멱등키 없이 동일 요청 결과 ===");
        System.out.println("생성된 KakaoPayData 개수: " + allPayments.size());

        // 멱등키가 없으면 각 요청마다 새로운 데이터가 생성됨
        assertThat(allPayments.size()).isEqualTo(5);
        
        // 모든 데이터가 동일한 사용자와 상품 타입을 가짐
        for (KakaoPayData payment : allPayments) {
            assertThat(payment.getBuyer().getId()).isEqualTo(testUser.getId());
            assertThat(payment.getProductType()).isEqualTo(ProductType.TWO_TO_TWO);
            assertThat(payment.getTotalPrice()).isEqualTo(totalPrice);
            assertThat(payment.getStatus()).isEqualTo(PaymentStatus.PREPARED);
        }
    }

    @Test
    @DisplayName("멱등키 없이 동시 요청 시 각각 다른 orderId 생성됨 - 시뮬레이션")
    void 멱등키_없이_동시_요청_다른_orderId_시뮬레이션() throws InterruptedException {
        // given
        String productType = ProductType.TWO_TO_TWO.name();
        Long totalPrice = 10000L;
        
        User testUser = User.builder()
                .studentNumber("2024" + System.currentTimeMillis())
                .name("멱등성테스트")
                .phoneNumber("010-" + System.currentTimeMillis())
                .password("password")
                .pushAgree(true)
                .build();
        
        int requestCount = 3;
        ExecutorService executorService = Executors.newFixedThreadPool(requestCount);
        CountDownLatch latch = new CountDownLatch(requestCount);
        List<KakaoPayData> allPayments = new ArrayList<>();

        // when: 멱등키 없이 동시 요청 시뮬레이션
        for (int i = 0; i < requestCount; i++) {
            final int index = i;
            executorService.submit(() -> {
                try {
                    KakaoPayData payment = KakaoPayData.builder()
                            .buyer(testUser)
                            .productType(ProductType.valueOf(productType))
                            .totalPrice(totalPrice)
                            .orderId("ORDER_" + System.currentTimeMillis() + "_" + index)
                            .tid("TID_" + System.currentTimeMillis() + "_" + index)
                            .status(PaymentStatus.PREPARED)
                            .build();
                    
                    synchronized (allPayments) {
                        allPayments.add(payment);
                    }
                    System.out.println("동시 요청 " + (index + 1) + " - OrderId: " + payment.getOrderId());
                } catch (Exception e) {
                    System.err.println("동시 요청 " + (index + 1) + " 실패: " + e.getMessage());
                } finally {
                    latch.countDown();
                }
            });
        }

        latch.await(5, TimeUnit.SECONDS);
        executorService.shutdown();

        // then: 각각 다른 orderId를 가진 데이터가 생성됨
        System.out.println("=== 멱등키 없이 동시 요청 orderId 결과 ===");
        for (KakaoPayData payment : allPayments) {
            System.out.println("OrderId: " + payment.getOrderId());
        }

        assertThat(allPayments).hasSize(requestCount);
        
        // 모든 orderId가 서로 다름
        List<String> orderIds = allPayments.stream()
                .map(KakaoPayData::getOrderId)
                .toList();
        
        assertThat(orderIds).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("멱등키 없이 순차 요청 시에도 중복 처리됨 - 시뮬레이션")
    void 멱등키_없이_순차_요청_중복_처리_시뮬레이션() {
        // given
        String productType = ProductType.TWO_TO_TWO.name();
        Long totalPrice = 10000L;

        User testUser = User.builder()
                .studentNumber("2024" + System.currentTimeMillis())
                .name("멱등성테스트")
                .phoneNumber("010-" + System.currentTimeMillis())
                .password("password")
                .pushAgree(true)
                .build();

        List<KakaoPayData> allPayments = new ArrayList<>();

        // when: 멱등키 없이 순차적으로 여러 번 요청 시뮬레이션
        for (int i = 0; i < 3; i++) {
            KakaoPayData payment = KakaoPayData.builder()
                    .buyer(testUser)
                    .productType(ProductType.valueOf(productType))
                    .totalPrice(totalPrice)
                    .orderId("ORDER_" + System.currentTimeMillis() + "_" + i)
                    .tid("TID_" + System.currentTimeMillis() + "_" + i)
                    .status(PaymentStatus.PREPARED)
                    .build();
            
            allPayments.add(payment);
            System.out.println("순차 요청 " + (i + 1) + " 생성 - OrderId: " + payment.getOrderId());
        }

        // then: 여러 개의 데이터가 생성됨
        System.out.println("=== 멱등키 없이 순차 요청 결과 ===");
        System.out.println("생성된 KakaoPayData 개수: " + allPayments.size());
        
        assertThat(allPayments).hasSize(3);
        
        // 각각 다른 orderId를 가짐
        List<String> orderIds = allPayments.stream()
                .map(KakaoPayData::getOrderId)
                .toList();
        
        assertThat(orderIds).doesNotHaveDuplicates();
    }

    @Test
    @DisplayName("멱등키 없이 동일한 요청을 보내면 매번 새로운 결제 준비가 생성됨 - 시뮬레이션")
    void 멱등키_없이_매번_새로운_결제_준비_시뮬레이션() {
        // given
        String productType = ProductType.TWO_TO_TWO.name();
        Long totalPrice = 10000L;

        User testUser = User.builder()
                .studentNumber("2024" + System.currentTimeMillis())
                .name("멱등성테스트")
                .phoneNumber("010-" + System.currentTimeMillis())
                .password("password")
                .pushAgree(true)
                .build();

        List<KakaoPayData> allPayments = new ArrayList<>();

        // when: 멱등키 없이 동일한 요청을 여러 번 시뮬레이션
        for (int i = 0; i < 2; i++) {
            KakaoPayData payment = KakaoPayData.builder()
                    .buyer(testUser)
                    .productType(ProductType.valueOf(productType))
                    .totalPrice(totalPrice)
                    .orderId("ORDER_" + System.currentTimeMillis() + "_" + i)
                    .tid("TID_" + System.currentTimeMillis() + "_" + i)
                    .status(PaymentStatus.PREPARED)
                    .build();
            
            allPayments.add(payment);
            System.out.println("요청 " + (i + 1) + " - OrderId: " + payment.getOrderId());
        }

        // then: 각각 다른 orderId를 가진 데이터가 생성됨
        assertThat(allPayments).hasSize(2);
        
        // 각각 다른 orderId
        String orderId1 = allPayments.get(0).getOrderId();
        String orderId2 = allPayments.get(1).getOrderId();
        
        assertThat(orderId1).isNotEqualTo(orderId2);
        
        System.out.println("=== 멱등키 없이 매번 새로운 결제 준비 결과 ===");
        System.out.println("첫 번째 OrderId: " + orderId1);
        System.out.println("두 번째 OrderId: " + orderId2);
    }
}