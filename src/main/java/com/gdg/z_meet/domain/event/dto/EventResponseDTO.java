package com.gdg.z_meet.domain.event.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

public class EventResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetPayDTO {
        Integer myHi;
        Integer teamHi;
        Integer ticket;
    }
}
