package com.gdg.z_meet.domain.settlement.controller;

import com.gdg.z_meet.domain.booth.entity.Category;
import com.gdg.z_meet.domain.booth.entity.Club;
import com.gdg.z_meet.domain.booth.entity.Place;
import com.gdg.z_meet.domain.booth.repository.ClubRepository;
import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.order.entity.enums.Bank;
import com.gdg.z_meet.domain.order.entity.enums.PaymentStatus;
import com.gdg.z_meet.domain.order.entity.enums.ProductType;
import com.gdg.z_meet.domain.order.repository.KakaoPayDataRepository;
import com.gdg.z_meet.domain.settlement.entity.Settlement;
import com.gdg.z_meet.domain.settlement.repository.SettlementRepository;
import com.gdg.z_meet.domain.settlement.service.SettlementService;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.http.ResponseEntity;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.bind.annotation.*;

import java.util.List;
import java.util.UUID;

@Slf4j
@Tag(name = "정산 테스트 API", description = "운영 데이터 없이 정산 로직을 테스트하기 위한 API")
@RestController
@RequestMapping("/api/v1/settlement/test")
@RequiredArgsConstructor
public class MockSettlementController {

    private final ClubRepository clubRepository;
    private final KakaoPayDataRepository kakaoPayDataRepository;
    private final SettlementRepository settlementRepository;
    private final SettlementService settlementService;
    private final UserRepository userRepository;

    @Operation(summary = "가상 정산 데이터 주입", description = "테스트용 부스와 미정산 결제 데이터를 생성합니다.")
    @PostMapping("/mock-data")
    @Transactional
    public ResponseEntity<String> createMockData() {
        // 1. 임의 사용자 조회 (혹은 생성)
        User user = userRepository.findAll().stream().findFirst().orElse(null);
        if (user == null) {
            return ResponseEntity.badRequest().body("No user found in DB. Please create a user first.");
        }

        // 2. 가상 부스(Club) 생성
        Club booth = Club.builder()
                .name("지밋 부스 " + UUID.randomUUID().toString().substring(0, 4))
                .place(Place.S_LEFT)
                .category(Category.FOOD)
                .bank(Bank.KAKAO)
                .account("3333-01-234567")
                .rep("홍길동")
                .build();
        clubRepository.save(booth);

        // 3. 미정산 결제 데이터 3건 생성 (소급 정산 테스트용)
        for (int i = 0; i < 3; i++) {
            KakaoPayData payment = KakaoPayData.builder()
                    .orderId(UUID.randomUUID().toString())
                    .tid("T" + UUID.randomUUID().toString().substring(0, 8))
                    .status(PaymentStatus.APPROVED)
                    .productType(ProductType.BOOTH_ITEM)
                    .totalPrice(10000L * (i + 1))
                    .buyer(user)
                    .club(booth)
                    .isSettled(false)
                    .build();
            kakaoPayDataRepository.save(payment);
        }

        log.info("Mock data created for Club: {}", booth.getName());
        return ResponseEntity.ok("Mock data created for booth: " + booth.getName());
    }

    @Operation(summary = "소급 정산 실행", description = "현재 미정산된 모든 성공 결제 건에 대해 정산을 수행합니다.")
    @PostMapping("/run")
    public ResponseEntity<String> runSettlement() {
        settlementService.processDelayedSettlement();
        return ResponseEntity.ok("Settlement process triggered manually.");
    }

    @Operation(summary = "정산 결과 조회", description = "생성된 모든 정산 내역을 조회합니다.")
    @GetMapping("/results")
    public ResponseEntity<List<Settlement>> getResults() {
        return ResponseEntity.ok(settlementRepository.findAll());
    }
}
