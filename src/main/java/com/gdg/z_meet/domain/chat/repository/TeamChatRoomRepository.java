package com.gdg.z_meet.domain.chat.repository;

import com.gdg.z_meet.domain.chat.entity.TeamChatRoom;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface TeamChatRoomRepository extends JpaRepository<TeamChatRoom,Long>, TeamChatRoomRepositoryCustom {
    List<TeamChatRoom> findByChatRoomId(Long chatRoomId);

    Optional<TeamChatRoom> findFirstByChatRoomId(Long chatRoomId);
}
