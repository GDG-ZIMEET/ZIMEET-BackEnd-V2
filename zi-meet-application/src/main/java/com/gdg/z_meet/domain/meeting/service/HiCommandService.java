package com.gdg.z_meet.domain.meeting.service;

import com.gdg.z_meet.domain.meeting.dto.MeetingRequestDTO;

public interface HiCommandService {
    void sendHi(MeetingRequestDTO.HiDto hiDto);
    void sendUserHi(MeetingRequestDTO.HiDto hiDto);
    void sendTeamHi(MeetingRequestDTO.HiDto hiDto);
    void refuseHi(MeetingRequestDTO.HiDto hiDto);
}
