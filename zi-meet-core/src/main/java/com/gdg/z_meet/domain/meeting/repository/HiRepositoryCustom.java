package com.gdg.z_meet.domain.meeting.repository;

import java.util.List;

public interface HiRepositoryCustom {
    List<Long> findUserIdsToNotGetHi();
    List<Long> findTeamIdToNotGetHi();
}
