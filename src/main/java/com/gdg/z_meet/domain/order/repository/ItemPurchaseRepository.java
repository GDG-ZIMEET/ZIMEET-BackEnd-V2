package com.gdg.z_meet.domain.order.repository;

import com.gdg.z_meet.domain.order.entity.ItemPurchase;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ItemPurchaseRepository extends JpaRepository<ItemPurchase, Long> {
    void deleteByBuyerId(Long buyerId);
}
