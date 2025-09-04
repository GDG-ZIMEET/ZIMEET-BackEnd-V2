package com.gdg.z_meet.domain.meeting.converter;

import com.gdg.z_meet.domain.meeting.dto.MeetingResponseDTO;
import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.meeting.entity.UserTeam;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.entity.enums.Level;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

import static com.gdg.z_meet.domain.meeting.entity.enums.Verification.COMPLETE;

public class MeetingConverter {

    public static MeetingResponseDTO.GetTeamGalleryDTO toGetTeamGalleryDTO(List<Team> teamList,
                                                                           Map<Long, List<String>> emojiList,
                                                                           Map<Long, List<String>> majorList,
                                                                           Map<Long, Double> age,
                                                                           Map<Long, List<String>> musicList){

        List<MeetingResponseDTO.GetPreTeamDTO> teamDTOS = teamList.stream()
                .map(team -> MeetingResponseDTO.GetPreTeamDTO.builder()
                        .teamId(team.getId())
                        .emoji(emojiList.get(team.getId()))
                        .name(team.getName())
                        .verification(team.getVerification() == COMPLETE ? 1 : 0)
                        .major(majorList.get(team.getId()))
                        .age(Math.round(age.get(team.getId()) * 10.0) / 10.0)
                        .music(musicList.get(team.getId()))
                        .build())
                .collect(Collectors.toList());

        return MeetingResponseDTO.GetTeamGalleryDTO.builder()
                .teamList(teamDTOS)
                .build();
    }

    public static MeetingResponseDTO.GetTeamDTO toGetTeamDTO(User u, Team team, List<User> users, Boolean hi){

        List<MeetingResponseDTO.GetTeamUserDTO> teamUserDTOS = users.stream()
                .map(user -> MeetingResponseDTO.GetTeamUserDTO.builder()
                        .userId(user.getId())
                        .emoji(user.getUserProfile().getEmoji())
                        .nickname(user.getUserProfile().getNickname())
                        .age(user.getUserProfile().getAge())
                        .studentNumber(user.getStudentNumber().substring(2, 4))
                        .major(String.valueOf(user.getUserProfile().getMajor().getDisplayName()))
                        .music(String.valueOf(user.getUserProfile().getMusic()))
                        .mbti(String.valueOf(user.getUserProfile().getMbti()))
                        .style(String.valueOf(user.getUserProfile().getStyle()))
                        .idealType(String.valueOf(user.getUserProfile().getIdealType()))
                        .idealAge(String.valueOf(user.getUserProfile().getIdealAge()))
                        .build())
                .collect(Collectors.toList());

        return MeetingResponseDTO.GetTeamDTO.builder()
                .teamId(team.getId())
                .name(team.getName())
                .verification(team.getVerification() == COMPLETE ? 1 : 0)
                .gender(String.valueOf(team.getGender()))
                .userList(teamUserDTOS)
                .hi(hi)
                .level(String.valueOf(u.getUserProfile().getLevel()))
                .build();
    }

    public static MeetingResponseDTO.GetPreMyTeamDTO toGetPreMyTeamDTO(Team team, List<String> emojiList){

        return MeetingResponseDTO.GetPreMyTeamDTO.builder()
                .teamId(team.getId())
                .emoji(emojiList)
                .name(team.getName())
                .verification(team.getVerification() == COMPLETE ? 1 : 0)
                .build();
    }

    public static MeetingResponseDTO.GetMyTeamDTO toGetMyTeamDTO(Team team, List<User> users){

        List<MeetingResponseDTO.GetTeamUserDTO> teamUserDTOS = users.stream()
                .map(user -> MeetingResponseDTO.GetTeamUserDTO.builder()
                        .userId(user.getId())
                        .emoji(user.getUserProfile().getEmoji())
                        .nickname(user.getUserProfile().getNickname())
                        .age(user.getUserProfile().getAge())
                        .studentNumber(user.getStudentNumber().substring(2, 4))
                        .major(String.valueOf(user.getUserProfile().getMajor().getDisplayName()))
                        .music(String.valueOf(user.getUserProfile().getMusic()))
                        .mbti(String.valueOf(user.getUserProfile().getMbti()))
                        .style(String.valueOf(user.getUserProfile().getStyle()))
                        .idealType(String.valueOf(user.getUserProfile().getIdealType()))
                        .idealAge(String.valueOf(user.getUserProfile().getIdealAge()))
                        .build())
                .collect(Collectors.toList());

        return MeetingResponseDTO.GetMyTeamDTO.builder()
                .teamId(team.getId())
                .name(team.getName())
                .verification(team.getVerification() == COMPLETE ? 1 : 0)
                .gender(String.valueOf(team.getGender()))
                .userList(teamUserDTOS)
                .build();
    }

    public static MeetingResponseDTO.GetMyTeamHiDTO toGetMyTeamHiDTO(Team team){

        return MeetingResponseDTO.GetMyTeamHiDTO.builder()
                .hi(team.getHi())
                .build();
    }

    public static UserTeam toUserTeam(User user, Team team) {

        return UserTeam.builder()
                .user(user)
                .team(team)
                .build();
    }

    public static MeetingResponseDTO.GetSearchListDTO GetSearchListDTO(List<User> users){

        List<MeetingResponseDTO.GetSearchDTO> searchDTOS = users.stream()
                .map(user -> MeetingResponseDTO.GetSearchDTO.builder()
                        .userId(user.getId())
                        .nickname(user.getUserProfile().getNickname())
                        .major(String.valueOf(user.getUserProfile().getMajor().getDisplayName()))
                        .grade(String.valueOf(user.getUserProfile().getGrade()))
                        .phoneNumber(user.getPhoneNumber())
                        .build())
                .collect(Collectors.toList());

        return MeetingResponseDTO.GetSearchListDTO.builder()
                 .searchList(searchDTOS)
                 .build();
    }

    public static MeetingResponseDTO.GetMyDeleteDTO toGetMyDeleteDTO(User user){

        return MeetingResponseDTO.GetMyDeleteDTO.builder()
                .leftDelete(user.getUserProfile().getLeftDelete())
                .build();
    }

    public static MeetingResponseDTO.GetUserGalleryDTO toGetUserGalleryDTO(List<User> userList){

        List<MeetingResponseDTO.GetPreUserDTO> userDTOS = userList.stream()
                .map(user -> {
                    UserProfile profile = user.getUserProfile();
                    return MeetingResponseDTO.GetPreUserDTO.builder()
                            .userId(user.getId())
                            .emoji(profile.getEmoji())
                            .nickname(profile.getNickname())
                            .verification(profile.getVerification() == COMPLETE ? 1 : 0)
                            .major(profile.getMajor().getShortName())
                            .age(profile.getAge())
                            .music(String.valueOf(profile.getMusic()))
                            .build();
                })
                .collect(Collectors.toList());

        return MeetingResponseDTO.GetUserGalleryDTO.builder()
                .userList(userDTOS)
                .build();
    }

    public static MeetingResponseDTO.GetPreMyProfileDTO toGetPreMyProfileDTO(User user){

        return MeetingResponseDTO.GetPreMyProfileDTO.builder()
                .userId(user.getId())
                .emoji(user.getUserProfile().getEmoji())
                .nickname(user.getUserProfile().getNickname())
                .verification(user.getUserProfile().getVerification() == COMPLETE ? 1 : 0)
                .profileStatus(String.valueOf(user.getUserProfile().getProfileStatus()))
                .build();
    }

    public static MeetingResponseDTO.GetProfileDTO toGetProfileDTO(User findUser, Boolean hi, Level level){
        UserProfile profile = findUser.getUserProfile();

        return MeetingResponseDTO.GetProfileDTO.builder()
                .userId(findUser.getId())
                .emoji(profile.getEmoji())
                .nickname(profile.getNickname())
                .verification(profile.getVerification() == COMPLETE ? 1 : 0)
                .gender(profile.getGender())
                .age(profile.getAge())
                .studentNumber(findUser.getStudentNumber().substring(2, 4))
                .major(profile.getMajor().getDisplayName())
                .music(profile.getMusic())
                .mbti(profile.getMbti())
                .style(profile.getStyle())
                .idealType(profile.getIdealType())
                .idealAge(profile.getIdealAge())
                .hi(hi)
                .level(level)
                .build();
    }
}