package com.gdg.z_meet.domain.chat.repository;

import com.gdg.z_meet.domain.meeting.entity.Team;

import java.util.Optional;

public interface TeamChatRoomRepositoryCustom {
    Optional<Team> findOtherTeamInChatRoom(Long chatRoomId, Long userId);
}
