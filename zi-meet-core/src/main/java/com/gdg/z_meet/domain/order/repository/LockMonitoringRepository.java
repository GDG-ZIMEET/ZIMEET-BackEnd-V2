package com.gdg.z_meet.domain.order.repository;

import com.gdg.z_meet.domain.order.entity.LockMonitoring;
import org.springframework.data.jpa.repository.JpaRepository;

/**
 * 락 모니터링 데이터를 위한 Repository
 */
public interface LockMonitoringRepository extends JpaRepository<LockMonitoring, Long> {
}

