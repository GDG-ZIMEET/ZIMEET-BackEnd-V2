package com.gdg.z_meet.domain.booth.dto;

import com.gdg.z_meet.domain.booth.entity.Category;
import com.gdg.z_meet.domain.booth.entity.Place;
import com.gdg.z_meet.domain.order.entity.enums.Bank;
import com.gdg.z_meet.global.validation.annotation.ValidEnum;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.Getter;

import java.util.List;

public class BoothRequestDTO {

    @Getter
    public static class CreateItemDTO {

        String name;
        String content;
    }

    @Getter
    public static class CreateClubDTO {

        @NotNull
        @ValidEnum(enumClass = Place.class)
        String place;

        @NotBlank
        String name;

        String rep;

        @NotNull
        @ValidEnum(enumClass = Category.class)
        String category;

        List<CreateItemDTO> itemList;

        @ValidEnum(enumClass = Bank.class)
        String bank;

        String account;

        String time;

        String info;
    }
}
