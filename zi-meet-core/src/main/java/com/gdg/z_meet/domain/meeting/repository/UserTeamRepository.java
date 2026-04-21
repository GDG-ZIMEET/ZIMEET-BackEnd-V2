package com.gdg.z_meet.domain.meeting.repository;

import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.meeting.entity.UserTeam;
import com.gdg.z_meet.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

public interface UserTeamRepository extends JpaRepository<UserTeam, Long> {

    @Query("SELECT CASE WHEN COUNT(ut) > 0 THEN true ELSE false END FROM UserTeam ut " +
            "WHERE ut.user.id = :userId AND ut.team.id = :teamId AND ut.team.activeStatus = 'ACTIVE'")
    boolean existsByUserIdAndTeamIdAndActiveStatus(@Param("userId") Long userId, @Param("teamId") Long teamId);

    boolean existsByUserIdAndTeamId(Long userId, Long teamId);

    @Query("SELECT ut FROM UserTeam ut WHERE ut.team.id = :teamId AND ut.team.activeStatus = 'ACTIVE'")
    List<UserTeam> findByTeamIdAndActiveStatus(@Param("teamId") Long teamId);

    List<UserTeam> findByTeamId(Long teamId);

    List<UserTeam> findByTeamIdIn(List<Long> teamIds);

    @Query("SELECT COUNT(ut) FROM UserTeam ut WHERE ut.team.id = :teamId")
    Long countByTeamId(@Param("teamId") Long teamId);

    @Query("SELECT ut FROM UserTeam ut WHERE ut.user.id = :userId AND ut.team.activeStatus = 'ACTIVE'")
    List<UserTeam> findByUserId(@Param("userId") Long userId);

    @Query("SELECT ut FROM UserTeam ut WHERE ut.user = :user AND ut.team.activeStatus = 'ACTIVE'")
    List<UserTeam> findByUser(@Param("user") User user);

    @Query("SELECT ut.user.id FROM UserTeam ut WHERE ut.team.id = :targetTeamId")
    List<Long> findUserIdsByTeamId(@Param("targetTeamId") Long targetTeamId);

    @Query("SELECT ut.user.id FROM UserTeam ut WHERE ut.team.id IN :teamIds")
    List<Long> findUserIdsByTeamIds(@Param("teamIds") List<Long> teamIds);

    @Query("SELECT ut FROM UserTeam ut JOIN FETCH ut.user WHERE ut.team = :team")
    List<UserTeam> findAllByTeam(@Param("team") Team team);
}