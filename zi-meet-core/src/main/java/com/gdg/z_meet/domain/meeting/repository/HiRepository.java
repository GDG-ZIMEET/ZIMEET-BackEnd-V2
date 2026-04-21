package com.gdg.z_meet.domain.meeting.repository;

import com.gdg.z_meet.domain.meeting.entity.Hi;
import com.gdg.z_meet.domain.meeting.entity.enums.HiStatus;
import com.gdg.z_meet.domain.meeting.entity.enums.HiType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;


public interface HiRepository extends JpaRepository<Hi,Long>, HiRepositoryCustom {
    Boolean existsByFromIdAndToIdAndHiStatusNotAndHiType(Long from, Long to, HiStatus status, HiType hiType);
    Hi findByFromIdAndToIdAndHiStatus(Long from, Long to, HiStatus status);

    @Query("SELECT DISTINCT h FROM Hi h WHERE " +
            "( (h.hiType = 'TEAM' AND h.toId IN (:teamIds)) OR (h.hiType = 'USER' AND h.toId = :userId) ) " +
            "AND h.hiStatus = 'NONE' ORDER BY h.createdAt DESC")
    List<Hi> findRecevieHiList(@Param("teamIds") List<Long> teamIds, @Param("userId") Long userId);

    @Query("SELECT DISTINCT h FROM Hi h WHERE " +
            "( (h.hiType = 'TEAM' AND h.fromId IN (:teamIds)) OR (h.hiType = 'USER' AND h.fromId = :userId) ) " +
            "AND h.hiStatus != 'DELETED' ORDER BY h.createdAt DESC")
    List<Hi> findSendHiList(@Param("teamIds") List<Long> teamIds, @Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true)
    @Query("UPDATE Hi h SET h.hiStatus = 'DELETED' WHERE h.fromId = :teamId OR h.toId = :teamId")
    void updateHiByTeamId(@Param("teamId") Long teamId);

    Optional<Hi> findByFromIdAndToIdAndHiType(Long fromId, Long toId, HiType hiType);

    Boolean existsByFromIdAndToIdAndHiType(Long fromId, Long toId, HiType hiType);

}
