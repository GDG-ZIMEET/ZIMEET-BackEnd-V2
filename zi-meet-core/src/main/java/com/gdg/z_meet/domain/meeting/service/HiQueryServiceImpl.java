package com.gdg.z_meet.domain.meeting.service;

import com.gdg.z_meet.domain.meeting.dto.MeetingResponseDTO;
import com.gdg.z_meet.domain.meeting.entity.Hi;
import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.meeting.entity.UserTeam;
import com.gdg.z_meet.domain.meeting.entity.enums.HiStatus;
import com.gdg.z_meet.domain.meeting.entity.enums.HiType;
import com.gdg.z_meet.domain.meeting.repository.HiRepository;
import com.gdg.z_meet.domain.meeting.repository.TeamRepository;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.AllArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Duration;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.stream.Collectors;

@Service
@AllArgsConstructor
public class HiQueryServiceImpl implements HiQueryService{

    private final HiRepository hiRepository;
    private final TeamRepository teamRepository;
    private final UserTeamRepository userTeamRepository;
    private final UserProfileRepository userProfileRepository;
    private final UserRepository userRepository;

    @Override
    @Transactional
    public List<MeetingResponseDTO.hiListDto> checkHiList(Long userId, String action){
        List<UserTeam> myTeams = userTeamRepository.findByUserId(userId);
        //if(myTeams.size()==0) throw new BusinessException(Code.TEAM_NOT_FOUND);
        List<Long> myTeamIds = myTeams.stream()
                .map(userTeam -> userTeam.getTeam().getId()) // UserTeam에서 teamId 추출
                .collect(Collectors.toList());

        List<Hi> hiList;
        if(action.equals("Receive")) {
            hiList = hiRepository.findRecevieHiList(myTeamIds, userId);
        }
        else{
            hiList = hiRepository.findSendHiList(myTeamIds, userId);
        }

        // 여러 개의 hiListDto 생성
        List<MeetingResponseDTO.hiListDto> hiListDtos = new ArrayList<>();
        for (Hi hi : hiList) {
            if(hi.getHiStatus()!=HiStatus.NONE && action.equals("Receive")) continue;

            Long teamId = action.equals("Receive") ? hi.getFromId() : hi.getToId();
            Long myteamId = action.equals("Receive") ? hi.getToId() : hi.getFromId();
            String otherName;

            if(hi.getHiType()== HiType.USER) {
                User otherUser = userRepository.findById(teamId).orElseThrow(() -> new BusinessException(Code.USER_NOT_FOUND));

                UserProfile userProfile = otherUser.getUserProfile();

                if (userProfile == null) { throw new BusinessException(Code.USER_PROFILE_NOT_FOUND); }

                otherName = userProfile.getNickname();
            }
            else{
                Team team = teamRepository.findById(teamId).orElseThrow(() -> new BusinessException(Code.TEAM_NOT_FOUND));
                otherName = team.getName();
            }

            List<UserProfile> userProfiles;

            if(hi.getHiType()== HiType.USER) {
                UserProfile profile = userProfileRepository.findByUserId(teamId)
                        .orElseThrow(() -> new BusinessException(Code.USER_PROFILE_NOT_FOUND));
                userProfiles = List.of(profile);
            }
            else {
                // 각 팀에 대해 UserProfileDto 만들기
                userProfiles = userProfileRepository.findByTeamId(teamId);
            }
            // UserProfileDto 리스트 생성
            List<MeetingResponseDTO.hiListDto.UserProfileDto> userProfileDtos = new ArrayList<>();
            int sum = 0;
            // 반복문 돌면서 DTO 객체를 리스트에 추가
            for (UserProfile userProfile : userProfiles) {
                sum += userProfile.getAge();
                userProfileDtos.add(new MeetingResponseDTO.hiListDto.UserProfileDto(
                        userProfile.getMajor(),
                        userProfile.getEmoji(),
                        userProfile.getMusic()
                ));
            }

            double averageAge = userProfiles.isEmpty() ? 0 : Math.round((sum / (double) userProfiles.size()) * 10.0) / 10.0;

            LocalDateTime sentTime = hi.getCreatedAt(); // Hi 생성 시간
            DateTimeFormatter formatter = DateTimeFormatter.ofPattern("MM-dd HH:mm");
            String dateTime = String.format("%s 전송", sentTime.format(formatter));

            if(action.equals("Receive")){
                LocalDateTime now = LocalDateTime.now(); // 현재 시간
                Duration duration = Duration.between(sentTime, now);

                long totalMinutesElapsed = duration.toMinutes(); // 보낸 후 총 경과된 분

                final long LIMIT_MINUTES = 12 * 60; // 12시간 제한
                long totalMinutesRemaining = LIMIT_MINUTES - totalMinutesElapsed;

                if (totalMinutesRemaining <= 0) {
                    hi.setChangeStatus(HiStatus.EXPIRED);
                    hiRepository.save(hi);
                    continue;
                }

                long remainingHours = totalMinutesRemaining / 60; // 남은 시간을 60으로 나눠서 시간 계산
                long remainingMinutes = totalMinutesRemaining % 60; // 나머지 분 계산

                dateTime = String.format("%d시간 %d분 남음", remainingHours, remainingMinutes);

                if(remainingHours<=0 || remainingMinutes<0){
                    hi.setChangeStatus(HiStatus.EXPIRED);
                    hiRepository.save(hi);
                    continue;
                }
            }

            // 하나의 hiListDto 생성
            MeetingResponseDTO.hiListDto hiDto = MeetingResponseDTO.hiListDto.builder()
                    .myTeamId(myteamId)
                    .teamId(teamId)
                    .teamName(otherName)
                    .type(hi.getHiType())
                    .userProfileDtos(userProfileDtos)
                    .age(averageAge)
                    .dateTime(dateTime)
                    .build();

            hiListDtos.add(hiDto); // hiListDto를 리스트에 추가
        }

        return hiListDtos;
    }
}
