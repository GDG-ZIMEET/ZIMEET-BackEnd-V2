package com.gdg.z_meet.domain.settlement.dto;

import com.gdg.z_meet.domain.settlement.entity.Settlement;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Getter
@NoArgsConstructor
public class SettlementStatusUpdateRequest {
    private Settlement.SettlementStatus status;
}
