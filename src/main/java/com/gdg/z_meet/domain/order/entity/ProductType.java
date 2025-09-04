package com.gdg.z_meet.domain.order.entity;

import lombok.Getter;

import java.util.Map;

@Getter
public enum ProductType {
    TWO_TO_TWO(Map.of(1, 400, 3, 1000, 10, 3000)),
    THREE_TO_THREE(Map.of(1, 400, 3, 1000, 10, 3000)),
    TICKET(Map.of(1, 500, 3, 1200, 8, 3000)),
    SEASON(Map.of(1,1900));

    private final Map<Integer, Integer> priceMap;

    ProductType(Map<Integer, Integer> priceMap) {
        this.priceMap = priceMap;
    }

    public static boolean isValid(String value) {
        for (ProductType type : values()) {
            if (type.name().equals(value)) {
                return true;
            }
        }
        return false;
    }
}

