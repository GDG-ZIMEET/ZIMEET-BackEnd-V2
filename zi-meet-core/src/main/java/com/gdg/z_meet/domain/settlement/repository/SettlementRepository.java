package com.gdg.z_meet.domain.settlement.repository;

import com.gdg.z_meet.domain.settlement.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import java.time.LocalDate;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    Optional<Settlement> findByClubIdAndSettlementDate(Long clubId, LocalDate settlementDate);

    Page<Settlement> findByStatus(Settlement.SettlementStatus status, Pageable pageable);

    Page<Settlement> findBySettlementDateBetween(LocalDate startDate, LocalDate endDate, Pageable pageable);

    Page<Settlement> findByClubId(Long clubId, Pageable pageable);

    long countByStatus(Settlement.SettlementStatus status);

    java.util.Optional<Settlement> findTopByClubIdOrderBySettlementDateDesc(Long clubId);

    @Query("SELECT COALESCE(SUM(s.settlementAmount), 0) FROM Settlement s WHERE s.status = :status")
    Long sumSettlementAmountByStatus(@Param("status") Settlement.SettlementStatus status);
}
