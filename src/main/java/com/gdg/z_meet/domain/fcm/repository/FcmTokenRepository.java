package com.gdg.z_meet.domain.fcm.repository;

import com.gdg.z_meet.domain.fcm.entity.FcmToken;
import com.gdg.z_meet.domain.user.entity.User;
import jakarta.persistence.LockModeType;
import org.springframework.data.domain.Pageable;
import org.springframework.data.domain.Slice;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface FcmTokenRepository extends JpaRepository<FcmToken, Long> {

    void deleteAllByUser(User user);

    List<FcmToken> findAllByUser(User user);

    void deleteByToken(String token);

    @Query(" SELECT ft FROM FcmToken ft JOIN FETCH ft.user u WHERE u.pushAgree = true")
    List<FcmToken> findAllByUserPushAgreeTrue();

    /**
     * 브로드캐스트용 페이징 조회 (메모리 최적화)
     */
    @Query("SELECT ft FROM FcmToken ft JOIN FETCH ft.user u WHERE u.pushAgree = true")
    Slice<FcmToken> findAllByUserPushAgreeTrueSlice(Pageable pageable);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("SELECT f FROM FcmToken f WHERE f.user = :user")
    Optional<FcmToken> findByUserForUpdate(@Param("user") User user);

    void deleteByUser(User user);

    Optional<FcmToken> findByUser(User user);

    Optional<FcmToken> findByToken(String token);
}
