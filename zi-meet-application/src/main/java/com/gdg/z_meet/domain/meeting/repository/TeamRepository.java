package com.gdg.z_meet.domain.meeting.repository;

import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.meeting.entity.enums.Event;
import com.gdg.z_meet.domain.meeting.entity.enums.TeamType;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.enums.Gender;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.time.LocalDateTime;
import java.util.List;
import java.util.Optional;

public interface TeamRepository extends JpaRepository<Team, Long> {

    @Query("SELECT t.id FROM Team t WHERE t.gender != :gender " +
            "AND t.teamType = :teamType AND t.event = :event AND t.activeStatus = 'ACTIVE'")
    List<Long> findAllByTeamType(@Param("gender") Gender gender, @Param("teamType") TeamType teamType, @Param("event") Event event);

    @Query("SELECT t FROM Team t WHERE t.id IN (SELECT ut.team.id FROM UserTeam ut WHERE ut.user.id = :userId) " +
            "AND t.teamType = :teamType AND t.event = :event AND t.activeStatus = 'ACTIVE'")
    Optional<Team> findByTeamType(@Param("userId") Long userId, @Param("teamType") TeamType teamType, @Param("event") Event event);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Team t " +
            "WHERE t.name = :name AND t.activeStatus = 'ACTIVE' AND t.event = :event")
    Boolean existsByName(@Param("name") String name, @Param("event") Event event);

    List<Team> findByIdIn(List<Long> teamIds);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Team t " +
            "JOIN UserTeam ut ON ut.team = t " +
            "WHERE ut.user.id IN :userIds AND t.teamType = :teamType AND t.activeStatus = 'ACTIVE' AND t.event = :event")
    Boolean existsAnyMemberByTeamType(@Param("userIds") List<Long> userIds, @Param("teamType") TeamType teamType, @Param("event") Event event);

    @Query("SELECT CASE WHEN COUNT(t) > 0 THEN true ELSE false END FROM Team t " +
            "WHERE t.id = :teamId AND t.activeStatus = 'ACTIVE'")
    Boolean existsByIdAndActiveStatus(@Param("teamId") Long teamId);

    @Query("SELECT t FROM Team t WHERE t.id = :teamId AND t.event = :event")
    Optional<Team> findByIdAndEvent(@Param("teamId") Long teamId, @Param("event") Event event);

    @Query("SELECT t FROM Team t JOIN UserTeam ut ON t.id = ut.team.id WHERE ut.user = :user AND t.event = :event AND t.activeStatus = 'ACTIVE'")
    List<Team> findAllByUser(@Param("user") User user, @Param("event") Event event);

    @Query("SELECT t FROM Team t JOIN UserTeam ut ON t.id = ut.team.id " +
            "WHERE ut.user = :user AND t.event = :event AND t.activeStatus = 'ACTIVE'")
    Optional<Team> findByUser(@Param("user") User user, @Param("event") Event event);


    @Query("""
        SELECT u FROM User u
        WHERE u.createdAt <= :threshold
        AND u.fcmSendTwoTwo = false
        AND NOT EXISTS (
            SELECT 1 FROM UserTeam ut
            WHERE ut.user.id = u.id AND ut.team.teamType = 'TWO_TO_TWO'
        )
    """)
    List<User> findUsersNotInTwoToTwoTeam(@Param("threshold") LocalDateTime threshold);
}
