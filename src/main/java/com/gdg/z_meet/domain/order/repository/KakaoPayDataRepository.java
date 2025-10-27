package com.gdg.z_meet.domain.order.repository;

import com.gdg.z_meet.domain.order.entity.KakaoPayData;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface KakaoPayDataRepository extends JpaRepository<KakaoPayData, Long> {
    Optional<KakaoPayData> findByOrderId(String orderId);
}
