package com.gdg.z_meet.domain.meeting.dto;

import com.gdg.z_meet.domain.meeting.entity.enums.MatchingStatus;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class RandomResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetTicketDTO {
        Integer ticket;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class UserMatchingDTO {
        Long userId;
        String emoji;
        String gender;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class MatchingDTO {
        String groupId;
        List<UserMatchingDTO> userList;
        MatchingStatus matchingStatus;
    }
}
