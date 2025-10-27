package com.gdg.z_meet.domain.order.repository;

import com.gdg.z_meet.domain.order.entity.ItemPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KakaoItemPurchaseRepository extends JpaRepository<ItemPurchase, Long> {
    boolean existsByOrderId(String orderId);
    Optional<ItemPurchase> findByOrderId(String orderId);
}
