package com.gdg.z_meet.domain.settlement.controller;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import com.gdg.z_meet.domain.settlement.dto.SettlementResponseDTO;
import com.gdg.z_meet.domain.settlement.dto.SettlementStatusUpdateRequest;
import com.gdg.z_meet.domain.settlement.entity.Settlement;
import com.gdg.z_meet.domain.settlement.service.SettlementService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

import java.time.LocalDate;
import java.util.List;

@Slf4j
@Tag(name = "어드민 정산 API", description = "어드민 전용 정산 관리 API")
@RestController
@RequestMapping("/api/v1/admin/settlements")
@RequiredArgsConstructor
public class AdminSettlementController {

    private final SettlementService settlementService;

    @Operation(summary = "정산 내역 목록 조회", description = "어드민이 생성된 정산 데이터를 필터링 및 페이지네이션으로 조회합니다.")
    @GetMapping
    public ResponseEntity<Page<SettlementResponseDTO>> getSettlements(
            @RequestParam(required = false) Settlement.SettlementStatus status,
            @RequestParam(required = false) Long clubId,
            @RequestParam(required = false) LocalDate startDate,
            @RequestParam(required = false) LocalDate endDate,
            Pageable pageable) {
        Page<SettlementResponseDTO> settlements = settlementService.getSettlements(status, clubId, startDate, endDate, pageable)
                .map(SettlementResponseDTO::from);
        return ResponseEntity.ok(settlements);
    }

    @Operation(summary = "정산 단건 상세 조회", description = "단건 정산 데이터를 조회합니다.")
    @GetMapping("/{settlementId}")
    public ResponseEntity<SettlementResponseDTO> getSettlement(@PathVariable Long settlementId) {
        Settlement settlement = settlementService.getSettlementDetail(settlementId);
        return ResponseEntity.ok(SettlementResponseDTO.from(settlement));
    }

    @Operation(summary = "정산 대상 결제 리스트 조회", description = "해당 정산 건에 포함된 실제 결제건들을 조회합니다.")
    @GetMapping("/{settlementId}/payments")
    public ResponseEntity<List<KakaoPayData>> getSettlementPayments(@PathVariable Long settlementId) {
        List<KakaoPayData> payments = settlementService.getPaymentsBySettlement(settlementId);
        return ResponseEntity.ok(payments);
    }

    @Operation(summary = "수동/일괄 정산 실행", description = "현재 미정산된 모든 결제건에 대해 정산을 수행합니다.")
    @PostMapping("/execute")
    public ResponseEntity<String> executeSettlement() {
        settlementService.processDelayedSettlement();
        return ResponseEntity.ok("정산 처리가 완료되었습니다.");
    }

    @Operation(summary = "정산 상태 변경(송금 완료)", description = "정산 상태를 변경(PAID 등)합니다.")
    @PatchMapping("/{settlementId}/status")
    public ResponseEntity<String> updateSettlementStatus(
            @PathVariable Long settlementId,
            @RequestBody SettlementStatusUpdateRequest request) {
        settlementService.updateSettlementStatus(settlementId, request.getStatus());
        return ResponseEntity.ok("정산 상태가 " + request.getStatus() + "로 변경되었습니다.");
    }
}
