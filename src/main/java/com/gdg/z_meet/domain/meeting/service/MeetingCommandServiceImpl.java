package com.gdg.z_meet.domain.meeting.service;

import com.gdg.z_meet.domain.meeting.converter.MeetingConverter;
import com.gdg.z_meet.domain.meeting.dto.MeetingRequestDTO;
import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.meeting.entity.enums.Event;
import com.gdg.z_meet.domain.meeting.entity.enums.TeamType;
import com.gdg.z_meet.domain.meeting.entity.UserTeam;
import com.gdg.z_meet.domain.meeting.repository.HiRepository;
import com.gdg.z_meet.domain.meeting.repository.TeamRepository;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.enums.Gender;
import com.gdg.z_meet.domain.user.entity.enums.ProfileStatus;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Set;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class MeetingCommandServiceImpl implements MeetingCommandService {

    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final TeamRepository teamRepository;
    private final UserTeamRepository userTeamRepository;
    private final Event event = Event.AU_2025;
    private final HiRepository hiRepository;

    @Override
    @Transactional
    public void createTeam(Long userId, TeamType teamType, MeetingRequestDTO.CreateTeamDTO request) {

        Set<Long> memberIds = new HashSet<>(request.getTeamMember());
        memberIds.add(userId);

        // 팀명 중복 확인
        if (teamRepository.existsByName(request.getName(), event)) {
            throw new BusinessException(Code.NAME_ALREADY_EXIST);
        }

        // 팀원 수 확인
        int userCount = memberIds.size();
        if (teamType == TeamType.TWO_TO_TWO && userCount != 2) {
            throw new BusinessException(Code.TEAM_TYPE_MISMATCH);
        } else if (teamType == TeamType.THREE_TO_THREE && userCount != 3) {
            throw new BusinessException(Code.TEAM_TYPE_MISMATCH);
        }

        // 팀 존재하는지 확인
        if (teamRepository.existsAnyMemberByTeamType(new ArrayList<>(memberIds), teamType, event)) {
            throw new BusinessException(Code.TEAM_ALREADY_EXIST);
        }

        // 성별 확인
        List<User> teamMembers = userRepository.findAllByIdWithProfile(new ArrayList<>(memberIds));
        Gender gender = userProfileRepository.findByUserId(userId).get().getGender();
        if (teamMembers.stream()
                .anyMatch(user -> user.getUserProfile().getGender() != gender)) {
            throw new BusinessException(Code.TEAM_GENDER_MISMATCH);
        }

        Team newTeam = Team.builder()
                .teamType(teamType)
                .name(request.getName())
                .gender(gender)
                .event(event)
                .build();
        teamRepository.save(newTeam);

        List<UserTeam> userTeams = teamMembers.stream()
                .map(user -> MeetingConverter.toUserTeam(user, newTeam))
                .collect(Collectors.toList());
        userTeamRepository.saveAll(userTeams);
    }

    @Override
    @Transactional
    public void delTeam(Long userId, TeamType teamType) {

        Team team = teamRepository.findByTeamType(userId, teamType, event)
                .orElseThrow(() -> new BusinessException(Code.TEAM_NOT_FOUND));
        Long teamId = team.getId();

        // 삭제 기회 확인
        List<UserTeam> userTeams = userTeamRepository.findByTeamIdAndActiveStatus(teamId);
        List<Long> users = userTeams.stream()
                .map(UserTeam -> UserTeam.getUser().getId())
                .collect(Collectors.toList());

        List<User> teamMembers = userRepository.findAllByIdWithProfile(new ArrayList<>(users));

        if (teamMembers.stream()
                .anyMatch(user -> user.getUserProfile().getLeftDelete() <= 0)) {
            throw new BusinessException(Code.DELETE_LIMIT_EXCEEDED);
        }

        userProfileRepository.subtractDelete(users);
        team.inactivateTeam();
        delHi(teamId);
        teamRepository.save(team);

        if (teamRepository.existsByIdAndActiveStatus(teamId)) {
            throw new BusinessException(Code.TEAM_DELETE_FAILED);
        }
    }

    @Override
    @Transactional
    public void patchProfileStatus(Long userId, MeetingRequestDTO.PatchProfileStatusDTO request) {

        ProfileStatus status = ProfileStatus.valueOf(request.getStatus());
        if (!ProfileStatus.isUpdatable(status)) {
            throw new BusinessException(Code.INVALID_PROFILE_STATUS);
        }

        User user = userRepository.findByIdWithProfile(userId);

        if (user.getUserProfile().getProfileStatus() == status) {
            throw new BusinessException(
                    status == ProfileStatus.ACTIVE ? Code.PROFILE_ALREADY_ACTIVE : Code.PROFILE_ALREADY_INACTIVE
            );
        }

        user.getUserProfile().changeProfileStatus(status);
    }

    private void delHi(Long teamId){
        hiRepository.updateHiByTeamId(teamId);
    }
}