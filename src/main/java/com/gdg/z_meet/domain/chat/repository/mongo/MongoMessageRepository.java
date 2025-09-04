package com.gdg.z_meet.domain.chat.repository.mongo;

import com.gdg.z_meet.domain.chat.entity.Message;
import org.springframework.data.domain.Pageable;
import org.springframework.data.mongodb.repository.MongoRepository;
import org.springframework.data.mongodb.repository.Query;

import java.time.LocalDateTime;
import java.util.Collection;
import java.util.Date;
import java.util.List;

public interface MongoMessageRepository extends MongoRepository<Message, String> {
    // MongoDB에서 String 타입의 chatRoomId를 받아 메시지를 조회
    List<Message> findByChatRoomId(String chatRoomId, Pageable pageable);
    List<Message> findByUserId(String userId);
    void deleteByUserId(String userId);

    // MongoMessageRepository.java
    @Query(value = "{ 'chatRoomId': ?0 }", fields = "{ 'messageId': 1 }")
    List<Message> findMessageIdOnlyByChatRoomId(String chatRoomId);

    List<Message> findByChatRoomIdAndCreatedAtBefore(String chatRoomId, Date createdAt, Pageable pageable);
  
  
    List<Message> findByMessageIdIn(Collection<String> messageIds);
}
