package com.gdg.z_meet.domain.user.repository;

import com.gdg.z_meet.domain.meeting.entity.enums.Event;
import com.gdg.z_meet.domain.meeting.entity.enums.TeamType;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.enums.Gender;

import java.util.List;

public interface UserRepositoryCustom {
    
    List<User> findAllByNicknameWithProfile(Gender gender, String nickname, Long userId, TeamType teamType, Event event);
    
    List<User> findAllByPhoneNumberWithProfile(Gender gender, String phoneNumber, Long userId, TeamType teamType, Event event);
}
