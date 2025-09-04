package com.gdg.z_meet.domain.meeting.repository;

import com.gdg.z_meet.domain.meeting.entity.Matching;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.Optional;

public interface MatchingRepository extends JpaRepository<Matching, Long> {

    @Query("SELECT m FROM Matching m WHERE " +
            "m.id IN (SELECT um.matching.id FROM UserMatching um WHERE um.user.id = :userId)")
    Optional<Matching> findWaitingMatchingByUserId(@Param("userId")Long userId);

    @Query("SELECT m FROM Matching m WHERE " +
            "m.id IN (SELECT um.matching.id FROM UserMatching um WHERE um.user.id = :userId)" +
            "ORDER BY m.id DESC LIMIT 1")
    Optional<Matching> findCompleteMatchingByUserId(@Param("userId")Long userId);
}
