package com.gdg.z_meet.domain.settlement.dto;

import com.gdg.z_meet.domain.order.entity.enums.Bank;
import com.gdg.z_meet.domain.settlement.entity.Settlement;
import lombok.Builder;
import lombok.Getter;

import java.time.LocalDate;

@Getter
@Builder
public class SettlementResponseDTO {
    private Long settlementId;
    private Long clubId;
    private String clubName;
    private Long totalAmount;
    private Long feeAmount;
    private Long settlementAmount;
    private Settlement.SettlementStatus status;
    private LocalDate settlementDate;
    private Bank bank;
    private String account;

    public static SettlementResponseDTO from(Settlement settlement) {
        return SettlementResponseDTO.builder()
                .settlementId(settlement.getId())
                .clubId(settlement.getClub() != null ? settlement.getClub().getId() : null)
                .clubName(settlement.getClub() != null ? settlement.getClub().getName() : null)
                .totalAmount(settlement.getTotalAmount())
                .feeAmount(settlement.getFeeAmount())
                .settlementAmount(settlement.getSettlementAmount())
                .status(settlement.getStatus())
                .settlementDate(settlement.getSettlementDate())
                .bank(settlement.getBank())
                .account(settlement.getAccount())
                .build();
    }
}
