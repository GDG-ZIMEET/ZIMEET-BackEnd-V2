package com.gdg.z_meet.domain.meeting.repository;

import com.gdg.z_meet.domain.meeting.entity.QHi;
import com.gdg.z_meet.domain.meeting.entity.enums.HiStatus;
import com.gdg.z_meet.domain.meeting.entity.enums.HiType;
import com.querydsl.jpa.impl.JPAQueryFactory;
import lombok.RequiredArgsConstructor;

import java.time.LocalDateTime;
import java.util.List;


@RequiredArgsConstructor
public class HiRepositoryImpl implements HiRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    @Override
    public List<Long> findUserIdsToNotGetHi() {

        QHi hi = QHi.hi;

        LocalDateTime fourHoursAgo = LocalDateTime.now().minusHours(4);
        LocalDateTime fourHoursAgoToMinute = fourHoursAgo.withSecond(0).withNano(0);

        return queryFactory
                .select(hi.toId)
                .from(hi)
                .where(
                        hi.hiType.eq(HiType.USER),
                        hi.hiStatus.eq(HiStatus.NONE),
                        hi.fcmSendHiToUser.eq(false),
                        hi.createdAt.goe(fourHoursAgoToMinute),
                        hi.createdAt.lt(fourHoursAgoToMinute.plusMinutes(1))
                )
                .distinct()
                .fetch();
    }

    @Override
    public List<Long> findTeamIdToNotGetHi() {

        QHi hi = QHi.hi;

        LocalDateTime fourHoursAgo = LocalDateTime.now().minusHours(4);
        LocalDateTime fourHoursAgoToMinute = fourHoursAgo.withSecond(0).withNano(0);

        return queryFactory
                .select(hi.toId)
                .from(hi)
                .where(
                        hi.hiType.eq(HiType.TEAM),
                        hi.hiStatus.eq(HiStatus.NONE),
                        hi.fcmSendHiToTeam.eq(false),
                        hi.createdAt.goe(fourHoursAgoToMinute),
                        hi.createdAt.lt(fourHoursAgoToMinute.plusMinutes(1))
                )
                .distinct()
                .fetch();
    }
}
