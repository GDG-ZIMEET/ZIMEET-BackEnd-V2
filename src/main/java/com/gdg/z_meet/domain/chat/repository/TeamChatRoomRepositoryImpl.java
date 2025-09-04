package com.gdg.z_meet.domain.chat.repository;

import com.gdg.z_meet.domain.chat.entity.QJoinChat;
import com.gdg.z_meet.domain.chat.entity.QTeamChatRoom;
import com.gdg.z_meet.domain.meeting.entity.Team;
import com.querydsl.jpa.impl.JPAQueryFactory;

import java.util.Optional;
import java.util.List;


public class TeamChatRoomRepositoryImpl implements TeamChatRoomRepositoryCustom {

    private final JPAQueryFactory queryFactory;

    public TeamChatRoomRepositoryImpl(JPAQueryFactory queryFactory) {
        this.queryFactory = queryFactory;
    }

    @Override
    public Optional<Team> findOtherTeamInChatRoom(Long chatRoomId, Long userId) {

        QTeamChatRoom teamChatRoom = QTeamChatRoom.teamChatRoom;
        QJoinChat joinChat = QJoinChat.joinChat;

        // 현재 유저가 속한 팀 ID 목록
        List<Long> userTeamIds = queryFactory
                .select(teamChatRoom.team.id)
                .from(teamChatRoom)
                .join(joinChat).on(joinChat.chatRoom.id.eq(teamChatRoom.chatRoom.id))
                .where(teamChatRoom.chatRoom.id.eq(chatRoomId)
                        .and(joinChat.user.id.eq(userId)))
                .fetch();

        // 유저가 속하지 않은 팀 중 첫 번째
        Team team = queryFactory
                .select(teamChatRoom.team)
                .from(teamChatRoom)
                .where(teamChatRoom.chatRoom.id.eq(chatRoomId)
                        .and(teamChatRoom.team.id.notIn(userTeamIds)))
                .fetchFirst();

        return Optional.ofNullable(team);
    }
}
