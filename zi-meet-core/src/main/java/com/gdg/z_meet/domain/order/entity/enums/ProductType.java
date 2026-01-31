package com.gdg.z_meet.domain.order.entity.enums;

import lombok.Getter;

import java.util.Map;

@Getter
public enum ProductType {
    TWO_TO_TWO("2대2 미팅 하이", Map.of(1, 400, 3, 1000, 10, 3000)),
    THREE_TO_THREE("3대3 미팅 하이", Map.of(1, 400, 3, 1000, 10, 3000)),
    TICKET("1대1 티켓", Map.of(1, 500, 3, 1200, 8, 3000)),
    SEASON("ZI-MEET Plus 시즌권", Map.of(1, 1900));

    private final String desc;
    private final Map<Integer, Integer> priceMap;

    ProductType(String desc, Map<Integer, Integer> priceMap) {
        this.desc = desc;
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

    /**
     * 총 금액으로부터 증분 수량 계산
     * 
     * @param totalPrice 총 금액
     * @return 증분 수량
     */
    public int calculateIncreaseAmount(Long totalPrice) {
        return this.priceMap.entrySet().stream()
                .filter(entry -> entry.getValue().equals(totalPrice.intValue()))
                .map(Map.Entry::getKey)
                .findFirst()
                .orElseThrow(() -> new IllegalArgumentException("Invalid total price: " + totalPrice));
    }
}
