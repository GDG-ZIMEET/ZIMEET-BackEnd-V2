package com.gdg.z_meet.domain.fcm.repository;

import com.gdg.z_meet.domain.fcm.entity.FcmToken;
import com.gdg.z_meet.domain.user.entity.User;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;

import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    void deleteAllByUser(User user);


    List<FcmToken> findAllByUser(User user);

    void deleteByToken(String token);

    @Query(" SELECT ft FROM FcmToken ft JOIN FETCH ft.user u WHERE u.pushAgree = true")
    List<FcmToken> findAllByUserPushAgreeTrue();

    void deleteByUser(User user);

    Optional<FcmToken> findByUser(User user);
}
