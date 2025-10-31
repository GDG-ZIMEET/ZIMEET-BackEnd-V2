package com.gdg.z_meet.domain.order.repository;

import com.gdg.z_meet.domain.order.entity.LockAudit;
import org.springframework.data.jpa.repository.JpaRepository;

public interface LockAuditRepository extends JpaRepository<LockAudit, Long> {
}

