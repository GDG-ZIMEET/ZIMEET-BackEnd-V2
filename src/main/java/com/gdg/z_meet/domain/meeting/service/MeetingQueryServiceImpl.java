package com.gdg.z_meet.domain.meeting.service;

import com.gdg.z_meet.domain.fcm.service.custom.FcmProfileMessageService;
import com.gdg.z_meet.domain.meeting.converter.MeetingConverter;
import com.gdg.z_meet.domain.meeting.dto.MeetingResponseDTO;
import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.meeting.entity.enums.ActiveStatus;
import com.gdg.z_meet.domain.meeting.entity.enums.Event;
import com.gdg.z_meet.domain.meeting.entity.enums.HiType;
import com.gdg.z_meet.domain.meeting.entity.enums.TeamType;
import com.gdg.z_meet.domain.meeting.entity.UserTeam;
import com.gdg.z_meet.domain.meeting.repository.HiRepository;
import com.gdg.z_meet.domain.meeting.repository.TeamRepository;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.entity.enums.Gender;
import com.gdg.z_meet.domain.user.entity.enums.Level;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Lazy;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;
import java.util.function.Function;

import java.util.stream.Collectors;
import java.util.stream.Stream;

@Service
@RequiredArgsConstructor
public class MeetingQueryServiceImpl implements MeetingQueryService {

    private final HiRepository hiRepository;
    private final UserRepository userRepository;
    private final UserProfileRepository userProfileRepository;
    private final TeamRepository teamRepository;
    private final UserTeamRepository userTeamRepository;
    private final Map<Long, CachedUserList> randomUserCache = new ConcurrentHashMap<>();
    private final Map<Long, CachedTeamList> randomTeamCache = new ConcurrentHashMap<>();

    @Lazy
    @Autowired
    private MeetingQueryServiceImpl self;      // 프록시 객체를 통한 트랜잭션 분리

    private final FcmProfileMessageService fcmProfileMessageService;

    private final Event event = Event.AU_2025;

    @Override
    @Transactional(readOnly = true)
    public MeetingResponseDTO.GetTeamGalleryDTO getTeamGallery(Long userId, TeamType teamType, Integer page) {

        Gender gender = userProfileRepository.findByUserId(userId).get().getGender();

        long now = System.currentTimeMillis();
        List<Long> teamIdList = getCachedTeamIds(userId, gender, teamType, now);

        int pageSize = 12;
        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, teamIdList.size());

        if (fromIndex >= teamIdList.size()) {
            return null;
        }

//        List<Long> pagedIdList = teamIdList.subList(fromIndex, toIndex);
//        List<Team> teamList = teamRepository.findByIdIn(pagedIdList);

        List<Team> teamList = teamRepository.findByIdIn(teamIdList);

        self.increaseTeamViewCountsAndSendFcm(teamList);

        Map<Long, List<String>> emojiList = collectEmoji(teamList);
        Map<Long, List<String>> majorList = collectMajor(teamList);
        Map<Long, Double> age = collectAge(teamList);
        Map<Long, List<String>> musicList = collectMusic(teamList);

        return MeetingConverter.toGetTeamGalleryDTO(teamList, emojiList, majorList, age, musicList);
    }

    @Transactional
    public void increaseTeamViewCountsAndSendFcm(List<Team> teamList) {
        for (Team team : teamList) {
            team.setViewCount(team.getViewCount() + 1);
        }

        fcmProfileMessageService.messagingProfileViewTwoTwoUsers(teamList);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingResponseDTO.GetTeamDTO getTeam(Long userId, Long teamId) {

        User user = userRepository.findByIdWithProfile(userId);

        if (userTeamRepository.existsByUserIdAndTeamIdAndActiveStatus(userId, teamId)) {
            throw new BusinessException(Code.INVALID_MY_TEAM_ACCESS);
        }
        Team team = teamRepository.findByIdAndEvent(teamId, event).orElseThrow(() -> new BusinessException(Code.TEAM_NOT_FOUND));
        if (team.getActiveStatus() == ActiveStatus.INACTIVE) {
            throw new BusinessException(Code.TEAM_ALREADY_DELETED);
        }

        validateTeamType(teamId, team.getTeamType());

        List<UserTeam> userTeams = userTeamRepository.findByTeamIdAndActiveStatus(teamId);
        List<User> users = userTeams.stream()
                .map(UserTeam::getUser)
                .collect(Collectors.toList());

        Team myTeam = teamRepository.findByTeamType(userId, team.getTeamType(), event)
                .orElseThrow(() -> new BusinessException(Code.MY_TEAM_NOT_FOUND));
        Boolean hi = hiRepository.existsByFromIdAndToIdAndHiType(myTeam.getId(), team.getId(), HiType.TEAM);

        return MeetingConverter.toGetTeamDTO(user, team, users, hi);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingResponseDTO.GetPreMyTeamDTO getPreMyTeam(Long userId, TeamType teamType) {

        Optional<Team> teamOptional = teamRepository.findByTeamType(userId, teamType, event);
        if (teamOptional.isEmpty()) {
            return null;
        }

        Team team = teamOptional.get();
        validateTeamType(team.getId(), teamType);

        List<UserTeam> userTeams = userTeamRepository.findByTeamIdAndActiveStatus(team.getId());
        List<String> emojiList = userTeams.stream()
                            .map(userTeam -> userTeam.getUser().getUserProfile().getEmoji())
                            .collect(Collectors.toList());

        return MeetingConverter.toGetPreMyTeamDTO(team, emojiList);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingResponseDTO.GetMyTeamDTO getMyTeam(Long userId, TeamType teamType) {

        Team team = teamRepository.findByTeamType(userId, teamType, event)
                .orElseThrow(() -> new BusinessException(Code.TEAM_NOT_FOUND));

        validateTeamType(team.getId(), teamType);

        List<UserTeam> userTeams = userTeamRepository.findByTeamIdAndActiveStatus(team.getId());
        List<User> users = userTeams.stream()
                .map(UserTeam::getUser)
                .collect(Collectors.toList());

        return MeetingConverter.toGetMyTeamDTO(team, users);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingResponseDTO.GetMyTeamHiDTO getMyTeamHi(Long userId, TeamType teamType) {

        Team team = teamRepository.findByTeamType(userId, teamType, event)
                .orElseThrow(() -> new BusinessException(Code.TEAM_NOT_FOUND));

        validateTeamType(team.getId(), teamType);

        return MeetingConverter.toGetMyTeamHiDTO(team);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingResponseDTO.CheckNameDTO checkName(String name) {

        Boolean exist = teamRepository.existsByName(name, event);

        return MeetingResponseDTO.CheckNameDTO.builder()
                .check(exist == Boolean.TRUE ? 0 : 1)
                .build();
    }


    @Override
    @Transactional(readOnly = true)
    public MeetingResponseDTO.GetSearchListDTO getSearch(Long userId, TeamType teamType, String nickname, String phoneNumber) {

        if (nickname == null && phoneNumber == null) {
            throw new BusinessException(Code.SEARCH_FILTER_NULL);
        }
        if (nickname != null && phoneNumber != null) {
            throw new BusinessException(Code.SEARCH_FILTER_EXCEEDED);
        }

        Gender gender = userProfileRepository.findByUserId(userId).get().getGender();
        List<User> users;

        if (nickname != null) {
            users = userRepository.findAllByNicknameWithProfile(gender, nickname, userId, teamType, event);
        } else if (phoneNumber.length() >= 7) {
            users = userRepository.findAllByPhoneNumberWithProfile(gender, phoneNumber, userId, teamType, event);
        } else {
            users = Collections.emptyList();
        }
        return MeetingConverter.GetSearchListDTO(users);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingResponseDTO.GetMyDeleteDTO getMyDelete(Long userId) {

        User user = userRepository.findByIdWithProfile(userId);

        return MeetingConverter.toGetMyDeleteDTO(user);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingResponseDTO.GetUserGalleryDTO getUserGallery(Long userId, Integer page) {

        UserProfile userProfile = userProfileRepository.findByUserId(userId)
                .orElseThrow(() -> new BusinessException(Code.USER_PROFILE_NOT_FOUND));

        Gender gender = userProfile.getGender();
        long now = System.currentTimeMillis();

        List<Long> userIdList = getCachedUserIds(userId, gender, now);

        int pageSize = 12;
        int fromIndex = page * pageSize;
        int toIndex = Math.min(fromIndex + pageSize, userIdList.size());

        if (fromIndex >= userIdList.size()) {
            return MeetingConverter.toGetUserGalleryDTO(Collections.emptyList());
        }

//        List<Long> pagedIdList = userIdList.subList(fromIndex, toIndex);
//        List<User> users = userRepository.findByIdInWithProfile(pagedIdList);

        List<User> users = userRepository.findByIdInWithProfile(userIdList);

        increaseViewCountsAndSendFcm(userIdList);

        return MeetingConverter.toGetUserGalleryDTO(users);
    }

    @Transactional
    public void increaseViewCountsAndSendFcm(List<Long> userIds) {
        List<UserProfile> profiles = userProfileRepository.findByUserIdIn(userIds);

        for (UserProfile profile : profiles) {
            profile.setViewCount(profile.getViewCount() + 1);
        }

        userProfileRepository.saveAll(profiles);
        fcmProfileMessageService.messagingProfileViewOneOneUsers(profiles);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingResponseDTO.GetPreMyProfileDTO getPreMyProfile(Long userId) {

        Optional<User> userOptional = userRepository.findByProfileStatus(userId);
        if (userOptional.isEmpty()) {
            return null;
        }

        User user = userOptional.get();

        return MeetingConverter.toGetPreMyProfileDTO(user);
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingResponseDTO.GetMyHiDTO getMyHi(Long userId) {

        Integer hi = userProfileRepository.findHiByUserId(userId)
                .orElseThrow(() -> new BusinessException(Code.USER_PROFILE_NOT_FOUND));

        return MeetingResponseDTO.GetMyHiDTO.builder()
                .hi(hi)
                .build();
    }

    @Override
    @Transactional(readOnly = true)
    public MeetingResponseDTO.GetProfileDTO getProfile(Long userId, Long profileId) {

        User user = userRepository.findByIdWithProfile(userId);

        if (userId.equals(profileId)) {
            throw new BusinessException(Code.INVALID_MY_PROFILE_ACCESS);
        }

        Gender gender = user.getUserProfile().getGender();
        User findUser = userRepository.findByGenderAndProfileStatus(profileId, gender)
                .orElseThrow(() -> new BusinessException(Code.USER_PROFILE_NOT_FOUND));

        Boolean hi = hiRepository.existsByFromIdAndToIdAndHiType(userId, profileId, HiType.USER);
        Level level = user.getUserProfile().getLevel();

        return MeetingConverter.toGetProfileDTO(findUser, hi, level);
    }


    private Map<Long, List<String>> collectEmoji(List<Team> teamList) {

        return collectTeamInfo(teamList,
                userTeam -> userTeam.getUser().getUserProfile().getEmoji(),
                false);
    }

    private Map<Long, List<String>> collectMajor(List<Team> teamList) {

        return collectTeamInfo(teamList,
                userTeam -> String.valueOf(userTeam.getUser().getUserProfile().getMajor().getShortName()),
                true);
    }

    private Map<Long, Double> collectAge(List<Team> teamList) {

        return teamList.stream().collect(Collectors.toMap(
                Team::getId, team -> userTeamRepository.findByTeamIdAndActiveStatus(team.getId()).stream()
                        .mapToInt(userTeam -> userTeam.getUser().getUserProfile().getAge())
                        .average()
                        .orElse(0.0)
        ));
    }

    private Map<Long, List<String>> collectMusic(List<Team> teamList) {

        return collectTeamInfo(teamList,
                userTeam -> String.valueOf(userTeam.getUser().getUserProfile().getMusic()),
                true);
    }

    @Transactional(readOnly = true)
    protected Map<Long, List<String>> collectTeamInfo(List<Team> teamList,
                                                      Function<UserTeam, String> mapper,
                                                      boolean distinct) {

        return teamList.stream().collect(Collectors.toMap(
                Team::getId, team -> {
                    Stream<String> stream = userTeamRepository.findByTeamIdAndActiveStatus(team.getId())
                            .stream()
                            .map(mapper);
                    return (distinct ? stream.distinct() : stream)
                            .collect(Collectors.toList());
                }
        ));
    }

    @Transactional(readOnly = true)
    protected void validateTeamType(Long teamId, TeamType teamType) {

        Long userCount = userTeamRepository.countByTeamId(teamId);
        if (teamType == TeamType.TWO_TO_TWO && userCount != 2) {
            throw new BusinessException(Code.TEAM_TYPE_MISMATCH);
        }
        if (teamType == TeamType.THREE_TO_THREE && userCount != 3) {
            throw new BusinessException(Code.TEAM_TYPE_MISMATCH);
        }
    }

    private List<Long> getCachedUserIds(Long userId, Gender gender, long now) {

        // Key: 로그인한 유저 id, Value: 랜덤 정렬한 유저 id 리스트, timestamp
        return randomUserCache.compute(userId, (id, cached) -> {
            if (cached == null || now - cached.timestamp > 10 * 60 * 1000) {
                List<Long> ids = userRepository.findAllByProfileStatus(userId, gender);
                Collections.shuffle(ids);
                return new CachedUserList(ids, now);
            }
            return cached;
        }).userIds;
    }

    private static class CachedUserList {
        List<Long> userIds;
        long timestamp;

        CachedUserList(List<Long> userIds, long timestamp) {
            this.userIds = userIds;
            this.timestamp = timestamp;
        }
    }

    private List<Long> getCachedTeamIds(Long userId, Gender gender, TeamType teamType, long now) {

        return randomTeamCache.compute(userId, (id, cached) -> {
            if (cached == null || now - cached.timestamp > 10 * 60 * 1000) {
                List<Long> ids = teamRepository.findAllByTeamType(gender, teamType, event);
                Collections.shuffle(ids);
                return new CachedTeamList(ids, now);
            }
            return cached;
        }).teamIds;
    }

    private static class CachedTeamList {
        List<Long> teamIds;
        long timestamp;

        CachedTeamList(List<Long> teamIds, long timestamp) {
            this.teamIds = teamIds;
            this.timestamp = timestamp;
        }
    }
}