package com.gdg.z_meet.domain.booth.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

import java.util.List;

public class BoothResponseDTO {

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class CreateClubDTO {
        Long clubId;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class preClubDTO {
        Long clubId;
        String name;
        String rep;
        String category;
        String account;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetAllClubDTO {
        List<preClubDTO> clubList;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class itemDTO {
        Long itemId;
        String name;
        String content;
    }

    @Builder
    @Getter
    @NoArgsConstructor
    @AllArgsConstructor
    public static class GetClubDTO {
        Long clubId;
        String name;
        String category;
        List<itemDTO> itemList;
        String account;
        String time;
        String info;
    }
}
