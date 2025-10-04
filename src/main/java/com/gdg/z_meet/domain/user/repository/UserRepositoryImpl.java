package com.gdg.z_meet.domain.user.repository;

import com.gdg.z_meet.domain.meeting.entity.QTeam;
import com.gdg.z_meet.domain.meeting.entity.QUserTeam;
import com.gdg.z_meet.domain.meeting.entity.enums.ActiveStatus;
import com.gdg.z_meet.domain.meeting.entity.enums.Event;
import com.gdg.z_meet.domain.meeting.entity.enums.TeamType;
import com.gdg.z_meet.domain.user.entity.QUser;
import com.gdg.z_meet.domain.user.entity.QUserProfile;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.enums.Gender;
import com.querydsl.core.types.dsl.BooleanExpression;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Repository;

import java.util.List;

@Repository
@RequiredArgsConstructor
public class UserRepositoryImpl implements UserRepositoryCustom {
    
    private final JPAQueryFactory queryFactory;
    
    @Override
    public List<User> findAllByNicknameWithProfile(Gender gender, String nickname, Long userId, TeamType teamType, Event event) {
        return findAllWithProfileCommon(gender, userId, teamType, event, QUserProfile.userProfile.nickname.startsWith(nickname));
    }
    
    @Override
    public List<User> findAllByPhoneNumberWithProfile(Gender gender, String phoneNumber, Long userId, TeamType teamType, Event event) {
        return findAllWithProfileCommon(gender, userId, teamType, event, QUser.user.phoneNumber.startsWith(phoneNumber));
    }
    
    private List<User> findAllWithProfileCommon(
            Gender gender, Long userId, TeamType teamType, Event event,
            BooleanExpression searchCondition
    ) {
        QUser user = QUser.user;
        QUserProfile userProfile = QUserProfile.userProfile;
        QUserTeam userTeam = QUserTeam.userTeam;
        QTeam team = QTeam.team;
        
        return queryFactory
                .selectFrom(user)
                .join(user.userProfile, userProfile).fetchJoin()
                .leftJoin(userTeam).on(userTeam.user.eq(user))
                .leftJoin(team).on(
                        userTeam.team.eq(team)
                                .and(team.teamType.eq(teamType))
                                .and(team.activeStatus.eq(ActiveStatus.ACTIVE))
                                .and(team.event.eq(event))
                )
                .where(
                        userProfile.gender.eq(gender),
                        user.id.ne(userId),
                        user.isDeleted.eq(false),
                        searchCondition,
                        team.id.isNull()
                )
                .fetch();
    }
}
