package com.gdg.z_meet.domain.booth.service;

import com.gdg.z_meet.domain.booth.dto.BoothRequestDTO;
import com.gdg.z_meet.domain.booth.dto.BoothResponseDTO;
import com.gdg.z_meet.domain.booth.entity.Club;

public interface BoothCommandService {

    BoothResponseDTO.CreateClubDTO createClub(BoothRequestDTO.CreateClubDTO request);
}
