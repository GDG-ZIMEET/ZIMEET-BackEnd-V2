package com.gdg.z_meet.domain.meeting.service;

import com.gdg.z_meet.domain.meeting.dto.RandomResponseDTO;

public interface RandomQueryService {

    RandomResponseDTO.GetTicketDTO getTicket(Long userId);
    RandomResponseDTO.MatchingDTO getMatching(Long userId);
}
