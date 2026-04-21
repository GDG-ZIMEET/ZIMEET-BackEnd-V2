package com.gdg.z_meet.domain.meeting.service;

import com.gdg.z_meet.domain.meeting.dto.MeetingRequestDTO;
import com.gdg.z_meet.domain.meeting.dto.MeetingResponseDTO;

import java.util.List;

public interface HiQueryService  {
        List<MeetingResponseDTO.hiListDto> checkHiList(Long teamId, String action);
}
