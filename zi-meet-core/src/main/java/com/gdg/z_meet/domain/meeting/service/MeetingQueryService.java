package com.gdg.z_meet.domain.meeting.service;

import com.gdg.z_meet.domain.meeting.dto.MeetingResponseDTO;
import com.gdg.z_meet.domain.meeting.entity.enums.TeamType;

public interface MeetingQueryService {

    MeetingResponseDTO.GetTeamGalleryDTO getTeamGallery(Long userId, TeamType teamType, Integer page);
    MeetingResponseDTO.GetTeamDTO getTeam(Long userId, Long teamId);

    MeetingResponseDTO.GetPreMyTeamDTO getPreMyTeam(Long userId, TeamType teamType);
    MeetingResponseDTO.GetMyTeamDTO getMyTeam(Long userId, TeamType teamType);
    MeetingResponseDTO.GetMyTeamHiDTO getMyTeamHi(Long userId, TeamType teamType);

    MeetingResponseDTO.CheckNameDTO checkName(String name);
    MeetingResponseDTO.GetSearchListDTO getSearch(Long userId, TeamType teamType, String nickname, String phoneNumber);
    MeetingResponseDTO.GetMyDeleteDTO getMyDelete(Long userId);

    MeetingResponseDTO.GetUserGalleryDTO getUserGallery(Long userId, Integer page);
    MeetingResponseDTO.GetPreMyProfileDTO getPreMyProfile(Long userId);
    MeetingResponseDTO.GetMyHiDTO getMyHi(Long userId);
    MeetingResponseDTO.GetProfileDTO getProfile(Long userId, Long profileId);
}
