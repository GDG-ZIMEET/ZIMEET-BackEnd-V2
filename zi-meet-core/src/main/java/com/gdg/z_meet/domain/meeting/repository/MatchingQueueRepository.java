package com.gdg.z_meet.domain.meeting.repository;

import com.gdg.z_meet.domain.meeting.entity.MatchingQueue;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface MatchingQueueRepository extends JpaRepository<MatchingQueue, Long> {

    @Query("SELECT CASE WHEN COUNT(q) > 0 THEN true ELSE false END FROM MatchingQueue q " +
            "WHERE q.user.id = :userId AND q.matchingStatus = 'WAITING'")
    boolean existsWaitingByUserId(@Param("userId") Long userId);

    @Query("SELECT q.groupId FROM MatchingQueue q GROUP BY q.groupId HAVING COUNT(q) < 4 ORDER BY MIN(q.createdAt)")
    List<String> findAllJoinableGroupIds();

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM MatchingQueue q WHERE q.groupId = :groupId")
    List<MatchingQueue> findByGroupIdWithLock(@Param("groupId") String groupId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM MatchingQueue q WHERE q.user.id = :userId AND q.matchingStatus = 'WAITING'")
    Optional<MatchingQueue> findWaitingByUserIdWithLock(@Param("userId") Long userId);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT q FROM MatchingQueue q WHERE q.user.id = :userId " +
            "ORDER BY q.id DESC")
    List<MatchingQueue> findByUserIdWithLock(@Param("userId") Long userId);

    @Modifying(flushAutomatically = true, clearAutomatically = true) // 벌크 연산
    @Query("DELETE FROM MatchingQueue q WHERE q.matchingStatus = 'COMPLETE' AND q.updatedAt < :time")
    void deleteByUpdatedBefore(@Param("time") LocalDateTime time);
}