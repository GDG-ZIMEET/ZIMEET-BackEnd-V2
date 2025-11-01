package com.gdg.z_meet.domain.fcm.unit.service.profile;

import com.gdg.z_meet.domain.fcm.service.profile.FcmProfileMessageServiceImpl;
import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.meeting.entity.UserTeam;
import com.gdg.z_meet.domain.meeting.repository.TeamRepository;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import com.gdg.z_meet.domain.fcm.service.producer.FcmMessageProducer;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
@DisplayName("FcmProfileMessageService 단위 테스트")
class FcmProfileMessageServiceImplTest {

    @Mock
    private FcmMessageProducer fcmMessageProducer;

    @Mock
    private UserTeamRepository userTeamRepository;

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private TeamRepository teamRepository;

    @InjectMocks
    private FcmProfileMessageServiceImpl fcmProfileMessageService;

    @Test
    @DisplayName("1:1 프로필 조회수 10회 달성 알림")
    void 프로필조회수_10회달성_알림() {
        User user = User.builder().id(1L).build();
        UserProfile userProfile = UserProfile.builder()
                .user(user)
                .viewCount(10)
                .lastNotified(0)
                .build();

        fcmProfileMessageService.messagingProfileViewOneOneUsers(List.of(userProfile));

        verify(fcmMessageProducer).sendSingleMessage(
                eq(1L), 
                eq("🥳 내 프로필을 10명이나 봤어요! 🎉 인기 폭발 시작이에요!"), 
                any()
        );
        verify(userProfileRepository).save(userProfile);
    }

    @Test
    @DisplayName("1:1 프로필 조회수 이미 알림 받은 경우 알림 안함")
    void 프로필조회수_이미알림받음_알림안함() {
        User user = User.builder().id(1L).build();
        UserProfile userProfile = UserProfile.builder()
                .user(user)
                .viewCount(10)
                .lastNotified(10)
                .build();

        fcmProfileMessageService.messagingProfileViewOneOneUsers(List.of(userProfile));

        verify(fcmMessageProducer, never()).sendSingleMessage(any(), any(), any());
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("2:2 팀 프로필 조회수 10회 달성 알림")
    void 팀프로필조회수_10회달성_알림() {
        User user1 = User.builder().id(1L).build();
        User user2 = User.builder().id(2L).build();
        
        Team team = Team.builder()
                .id(1L)
                .viewCount(10)
                .lastNotified(0)
                .build();

        UserTeam userTeam1 = UserTeam.builder().user(user1).team(team).build();
        UserTeam userTeam2 = UserTeam.builder().user(user2).team(team).build();

        when(userTeamRepository.findAllByTeam(team)).thenReturn(List.of(userTeam1, userTeam2));

        fcmProfileMessageService.messagingProfileViewTwoTwoUsers(List.of(team));

        verify(fcmMessageProducer, times(2)).sendSingleMessage(any(), any(), any());
        verify(teamRepository).save(team);
    }

    @Test
    @DisplayName("2:2 팀 프로필 조회수 이미 알림 받은 경우 알림 안함")
    void 팀프로필조회수_이미알림받음_알림안함() {
        Team team = Team.builder()
                .id(1L)
                .viewCount(10)
                .lastNotified(10)
                .build();

        fcmProfileMessageService.messagingProfileViewTwoTwoUsers(List.of(team));

        verify(fcmMessageProducer, never()).sendSingleMessage(any(), any(), any());
        verify(teamRepository, never()).save(any());
    }
}
