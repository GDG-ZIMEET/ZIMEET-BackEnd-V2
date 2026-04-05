package com.gdg.z_meet.domain.settlement.repository;

import com.gdg.z_meet.domain.settlement.entity.Settlement;
import org.springframework.data.jpa.repository.JpaRepository;
import java.time.LocalDate;
import java.util.Optional;

public interface SettlementRepository extends JpaRepository<Settlement, Long> {
    Optional<Settlement> findByClubIdAndSettlementDate(Long clubId, LocalDate settlementDate);
}
