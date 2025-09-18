package com.gdg.z_meet.domain.fcm.service.meeting;

import com.gdg.z_meet.domain.fcm.service.core.FcmDomainMessageService;

public interface FcmMeetingMessageService extends FcmDomainMessageService {
    
    void messagingNoneMeetingOneOneUsers();
    
    void messagingNoneMeetingTwoTwoUsers();
    
    void messagingHiToUser(Long targetUserId);
    
    void messagingNotAcceptHiToUser();
    
    void messagingHiToTeam(Long targetTeamId);
    
    void messagingNotAcceptHiToTeam();
}
