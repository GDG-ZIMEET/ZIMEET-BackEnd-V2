package com.gdg.z_meet.domain.meeting.dto;

import com.gdg.z_meet.domain.meeting.entity.enums.HiType;
import com.gdg.z_meet.domain.user.entity.enums.ProfileStatus;
import com.gdg.z_meet.global.validation.annotation.ValidEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import lombok.AllArgsConstructor;
import lombok.Builder;
import jakarta.validation.constraints.Size;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class MeetingRequestDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class HiDto {
        Long toId;
        Long fromId;
        HiType type; //USER or TEAM
    }


    @Getter
    public static class CreateTeamDTO {

        @NotBlank
        @Size(min = 1, max = 8)
        String name;

        @NotNull
        List<Long> teamMember;
    }

    @Getter
    public static class PatchProfileStatusDTO {

        @NotNull
        @ValidEnum(enumClass = ProfileStatus.class)
        String status;
    }
}
