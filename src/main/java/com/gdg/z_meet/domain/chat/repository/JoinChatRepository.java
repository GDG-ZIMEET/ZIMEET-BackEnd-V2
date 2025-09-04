package com.gdg.z_meet.domain.chat.repository;

import com.gdg.z_meet.domain.chat.entity.ChatRoom;
import com.gdg.z_meet.domain.chat.entity.JoinChat;
import com.gdg.z_meet.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface JoinChatRepository extends JpaRepository<JoinChat, Long> {
    List<JoinChat> findByChatRoomId(Long chatRoomId);
    List<JoinChat> findByUserId(Long userId);
    Optional<JoinChat> findByUserAndChatRoom(User user, ChatRoom chatRoom);
    @Query("SELECT uc.user FROM JoinChat uc WHERE uc.chatRoom.id = :chatRoomId AND uc.status = 'ACTIVE'")
    List<User>findUsersByChatRoomId(@Param("chatRoomId") Long chatRoomId);
    @Query("SELECT COUNT(jc) > 0 FROM JoinChat jc WHERE jc.user.id = :userId AND jc.chatRoom.id = :chatRoomId AND jc.status = 'ACTIVE'")
    Boolean existsByUserIdAndChatRoomIdAndStatusActive(@Param("userId") Long userId, @Param("chatRoomId") Long chatRoomId);
    @Query("SELECT jc.user.id FROM JoinChat jc WHERE jc.user.id IN :userIds AND jc.chatRoom.id = :chatRoomId")
    List<Long> findUserIdsByUserIdInAndChatRoomId(@Param("userIds") List<Long> userIds, @Param("chatRoomId") Long chatRoomId);
    @Query("SELECT jc FROM JoinChat jc WHERE jc.user.id = :userId AND jc.status = 'ACTIVE'")
    List<JoinChat> findByUserIdAndStatus(@Param("userId") Long userId);

    @Modifying
    @Query("UPDATE JoinChat j SET j.status = 'INACTIVE' WHERE j.user.id = :userId")
    void deleteJoinChatWithUser(@Param("userId") Long userId);

    @Query("SELECT jc FROM JoinChat jc WHERE jc.chatRoom.id = :chatRoomId AND jc.user.id != :userId")
    Optional<JoinChat> findUserByChatRoomIdAndUserNotUserId(@Param("chatRoomId") Long chatRoomId, @Param("userId") Long userId);


}