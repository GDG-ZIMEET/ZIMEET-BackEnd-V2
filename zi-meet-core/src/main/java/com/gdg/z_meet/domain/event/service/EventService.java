package com.gdg.z_meet.domain.event.service;

import com.gdg.z_meet.domain.event.Product;
import com.gdg.z_meet.domain.event.dto.EventResponseDTO;
import com.gdg.z_meet.domain.meeting.converter.MeetingConverter;
import com.gdg.z_meet.domain.meeting.dto.MeetingResponseDTO;
import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.meeting.entity.enums.Event;
import com.gdg.z_meet.domain.meeting.repository.TeamRepository;
import com.gdg.z_meet.domain.user.dto.UserRes;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

import static com.gdg.z_meet.domain.meeting.entity.enums.Verification.COMPLETE;

@Service
@RequiredArgsConstructor
public class EventService {

    private final TeamRepository teamRepository;
    private final UserRepository userRepository;
    private final Event event = Event.AU_2025;

    @Transactional
    public MeetingResponseDTO.GetMyDeleteDTO patchMyDelete(String name, String studentNumber) {

        User user = userRepository.findByNameAndStudentNumberWithProfile(name, studentNumber)
                .orElseThrow(() -> new BusinessException(Code.USER_NOT_FOUND));

        user.getUserProfile().addDelete();

        return MeetingConverter.toGetMyDeleteDTO(user);
    }

    @Transactional
    public UserRes.GetLevelDTO patchLevel(String name, String studentNumber) {

        User user = userRepository.findByNameAndStudentNumberWithProfile(name, studentNumber)
                .orElseThrow(() -> new BusinessException(Code.USER_NOT_FOUND));

        user.getUserProfile().upLevel();

        return UserRes.GetLevelDTO.builder()
                .userId(user.getId())
                .level(String.valueOf(user.getUserProfile().getLevel()))
                .build();
    }

    @Transactional
    public MeetingResponseDTO.GetVerificationDTO patchVerification(String name, String studentNumber) {

        User user = userRepository.findByNameAndStudentNumberWithProfile(name, studentNumber)
                .orElseThrow(() -> new BusinessException(Code.USER_NOT_FOUND));

        List<Team> teamList = teamRepository.findAllByUser(user, event);
        teamList.forEach(Team::patchVerification);

        List<MeetingResponseDTO.GetVerificationTeamDTO> teamDTOS = teamList.stream()
                .map(team -> MeetingResponseDTO.GetVerificationTeamDTO.builder()
                        .teamId(team.getId())
                        .verification(team.getVerification() == COMPLETE ? 1 : 0)
                        .build())
                .collect(Collectors.toList());

        return MeetingResponseDTO.GetVerificationDTO.builder()
                .teamList(teamDTOS)
                .build();
    }

    @Transactional
    public EventResponseDTO.GetPayDTO patchPay(String name, String studentNumber, Product product) {

        User user = userRepository.findByNameAndStudentNumberWithProfile(name, studentNumber)
                .orElseThrow(() -> new BusinessException(Code.USER_NOT_FOUND));

        Team team = product.needsTeam()
                ? teamRepository.findByUser(user, event)
                .orElseThrow(() -> new BusinessException(Code.TEAM_NOT_FOUND))
                : null;

        product.payProduct(user, team);

        return EventResponseDTO.GetPayDTO.builder()
                .myHi(user.getUserProfile().getHi())
                .teamHi(team != null ? team.getHi() : null)
                .ticket(user.getUserProfile().getTicket())
                .build();
    }
}
