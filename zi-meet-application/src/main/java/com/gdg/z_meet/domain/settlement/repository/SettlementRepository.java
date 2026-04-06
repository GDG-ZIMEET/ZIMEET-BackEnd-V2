package com.gdg.z_meet.domain.settlement.repository;

import com.gdg.z_meet.domain.settlement.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    Optional<Settlement> findByClubIdAndSettlementDate(Long clubId, LocalDate settlementDate);

    Page<Settlement> findByStatus(Settlement.SettlementStatus status, Pageable pageable);

    Page<Settlement> findBySettlementDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<Settlement> findByClubId(Long clubId, Pageable pageable);
}
