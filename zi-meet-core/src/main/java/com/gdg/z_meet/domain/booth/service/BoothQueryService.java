package com.gdg.z_meet.domain.booth.service;

import com.gdg.z_meet.domain.booth.dto.BoothResponseDTO;
import com.gdg.z_meet.domain.booth.entity.Club;

import java.util.List;

public interface BoothQueryService {

    BoothResponseDTO.GetAllClubDTO getAllClub(String place);
    BoothResponseDTO.GetClubDTO getClub(Long clubId);
}
