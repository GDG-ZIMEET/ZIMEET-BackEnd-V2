package com.gdg.z_meet.domain.fcm.service.profile;

import com.gdg.z_meet.domain.fcm.service.core.FcmDomainMessageService;
import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.user.entity.UserProfile;

import java.util.List;

public interface FcmProfileMessageService extends FcmDomainMessageService {
    
    void messagingProfileViewOneOneUsers(List<UserProfile> profiles);
    
    void messagingProfileViewTwoTwoUsers(List<Team> teams);
}
