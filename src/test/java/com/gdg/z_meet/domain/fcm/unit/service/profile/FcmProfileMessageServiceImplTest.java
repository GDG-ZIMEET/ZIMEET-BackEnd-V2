package com.gdg.z_meet.domain.fcm.unit.service.profile;

import com.gdg.z_meet.domain.fcm.service.profile.FcmProfileMessageServiceImpl;
import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.meeting.entity.UserTeam;
import com.gdg.z_meet.domain.meeting.repository.TeamRepository;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import com.gdg.z_meet.global.client.FcmMessageClient;
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
    private FcmMessageClient fcmMessageClient;

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
    void messagingProfileViewOneOneUsers_10Views() {
        // Given
        User user = User.builder().id(1L).build();
        UserProfile userProfile = UserProfile.builder()
                .user(user)
                .viewCount(10)
                .lastNotified(0)
                .build();

        when(fcmMessageClient.sendFcmMessage(any(), any(), any())).thenReturn(true);

        // When
        fcmProfileMessageService.messagingProfileViewOneOneUsers(List.of(userProfile));

        // Then
        verify(fcmMessageClient).sendFcmMessage(
                eq(1L), 
                eq("🥳 내 프로필을 10명이나 봤어요! 🎉 인기 폭발 시작이에요!"), 
                eq("인기있는 당신!! 어떤 사람들이 ZI밋에 있는지 확인해볼까요?🔥")
        );
        verify(userProfileRepository).save(userProfile);
    }

    @Test
    @DisplayName("1:1 프로필 조회수 50회 달성 알림")
    void messagingProfileViewOneOneUsers_50Views() {
        // Given
        User user = User.builder().id(1L).build();
        UserProfile userProfile = UserProfile.builder()
                .user(user)
                .viewCount(50)
                .lastNotified(10)
                .build();

        when(fcmMessageClient.sendFcmMessage(any(), any(), any())).thenReturn(true);

        // When
        fcmProfileMessageService.messagingProfileViewOneOneUsers(List.of(userProfile));

        // Then
        verify(fcmMessageClient).sendFcmMessage(
                eq(1L), 
                eq("🔥 벌써 50명이 다녀갔어요! 대세는 역시 나, 지금 확인해보세요!"), 
                any()
        );
        verify(userProfileRepository).save(userProfile);
    }

    @Test
    @DisplayName("1:1 프로필 조회수 100회 달성 알림")
    void messagingProfileViewOneOneUsers_100Views() {
        // Given
        User user = User.builder().id(1L).build();
        UserProfile userProfile = UserProfile.builder()
                .user(user)
                .viewCount(100)
                .lastNotified(50)
                .build();

        when(fcmMessageClient.sendFcmMessage(any(), any(), any())).thenReturn(true);

        // When
        fcmProfileMessageService.messagingProfileViewOneOneUsers(List.of(userProfile));

        // Then
        verify(fcmMessageClient).sendFcmMessage(
                eq(1L), 
                eq("💯 무려 100명이 당신을 봤어요! 관심 폭주 중이에요, 놓치지 마세요!"), 
                any()
        );
    }

    @Test
    @DisplayName("1:1 프로필 조회수 이미 알림 받은 경우")
    void messagingProfileViewOneOneUsers_AlreadyNotified() {
        // Given
        User user = User.builder().id(1L).build();
        UserProfile userProfile = UserProfile.builder()
                .user(user)
                .viewCount(10)
                .lastNotified(10)
                .build();

        // When
        fcmProfileMessageService.messagingProfileViewOneOneUsers(List.of(userProfile));

        // Then
        verify(fcmMessageClient, never()).sendFcmMessage(any(), any(), any());
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("1:1 프로필 FCM 전송 실패")
    void messagingProfileViewOneOneUsers_FcmFailure() {
        // Given
        User user = User.builder().id(1L).build();
        UserProfile userProfile = UserProfile.builder()
                .user(user)
                .viewCount(10)
                .lastNotified(0)
                .build();

        when(fcmMessageClient.sendFcmMessage(any(), any(), any())).thenReturn(false);

        // When
        fcmProfileMessageService.messagingProfileViewOneOneUsers(List.of(userProfile));

        // Then
        verify(fcmMessageClient).sendFcmMessage(any(), any(), any());
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("2:2 팀 프로필 조회수 10회 달성 알림")
    void messagingProfileViewTwoTwoUsers_10Views() {
        // Given
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
        when(fcmMessageClient.sendFcmMessage(any(), any(), any())).thenReturn(false); // success 로직이 반대

        // When
        fcmProfileMessageService.messagingProfileViewTwoTwoUsers(List.of(team));

        // Then
        verify(fcmMessageClient, times(2)).sendFcmMessage(
                any(), 
                eq("🥳 우리 팀 프로필을 10명이나 봤어요! 🎉 인기 폭발 시작이에요!"), 
                eq("인기있는 우리 팀!! 어떤 팀들이 ZI밋에 있는지 확인해볼까요?🔥")
        );
        verify(teamRepository).save(team);
    }

    @Test
    @DisplayName("2:2 팀 프로필 조회수 500회 달성 알림")
    void messagingProfileViewTwoTwoUsers_500Views() {
        // Given
        User user = User.builder().id(1L).build();
        Team team = Team.builder()
                .id(1L)
                .viewCount(500)
                .lastNotified(100)
                .build();

        UserTeam userTeam = UserTeam.builder().user(user).team(team).build();

        when(userTeamRepository.findAllByTeam(team)).thenReturn(List.of(userTeam));
        when(fcmMessageClient.sendFcmMessage(any(), any(), any())).thenReturn(false);

        // When
        fcmProfileMessageService.messagingProfileViewTwoTwoUsers(List.of(team));

        // Then
        verify(fcmMessageClient).sendFcmMessage(
                eq(1L), 
                eq("🌟 500명 돌파! 이 정도면 거의 스타 등장이죠? 지금 확인해봐요!"), 
                any()
        );
    }

    @Test
    @DisplayName("2:2 팀 프로필 조회수 1000회 달성 알림")
    void messagingProfileViewTwoTwoUsers_1000Views() {
        // Given
        User user = User.builder().id(1L).build();
        Team team = Team.builder()
                .id(1L)
                .viewCount(1000)
                .lastNotified(500)
                .build();

        UserTeam userTeam = UserTeam.builder().user(user).team(team).build();

        when(userTeamRepository.findAllByTeam(team)).thenReturn(List.of(userTeam));
        when(fcmMessageClient.sendFcmMessage(any(), any(), any())).thenReturn(false);

        // When
        fcmProfileMessageService.messagingProfileViewTwoTwoUsers(List.of(team));

        // Then
        verify(fcmMessageClient).sendFcmMessage(
                eq(1L), 
                eq("🏆 1000명 초과 달성! ZI밋에서 우리 팀의 인기가 뜨겁게 타오르고 있어요!"), 
                any()
        );
    }

    @Test
    @DisplayName("2:2 팀 프로필 조회수 이미 알림 받은 경우")
    void messagingProfileViewTwoTwoUsers_AlreadyNotified() {
        // Given
        Team team = Team.builder()
                .id(1L)
                .viewCount(10)
                .lastNotified(10)
                .build();

        // When
        fcmProfileMessageService.messagingProfileViewTwoTwoUsers(List.of(team));

        // Then
        verify(fcmMessageClient, never()).sendFcmMessage(any(), any(), any());
        verify(userTeamRepository, never()).findAllByTeam(any());
        verify(teamRepository, never()).save(any());
    }

    @Test
    @DisplayName("2:2 팀 프로필 모든 FCM 전송 성공")
    void messagingProfileViewTwoTwoUsers_AllFcmSuccess() {
        // Given
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
        when(fcmMessageClient.sendFcmMessage(any(), any(), any())).thenReturn(true); // 모두 성공

        // When
        fcmProfileMessageService.messagingProfileViewTwoTwoUsers(List.of(team));

        // Then
        verify(fcmMessageClient, times(2)).sendFcmMessage(any(), any(), any());
        verify(teamRepository, never()).save(team); // 모든 전송이 성공하면 저장 안됨 (로직상)
    }

    @Test
    @DisplayName("빈 프로필 리스트로 1:1 알림")
    void messagingProfileViewOneOneUsers_EmptyList() {
        // When
        fcmProfileMessageService.messagingProfileViewOneOneUsers(List.of());

        // Then
        verify(fcmMessageClient, never()).sendFcmMessage(any(), any(), any());
        verify(userProfileRepository, never()).save(any());
    }

    @Test
    @DisplayName("빈 팀 리스트로 2:2 알림")
    void messagingProfileViewTwoTwoUsers_EmptyList() {
        // When
        fcmProfileMessageService.messagingProfileViewTwoTwoUsers(List.of());

        // Then
        verify(fcmMessageClient, never()).sendFcmMessage(any(), any(), any());
        verify(teamRepository, never()).save(any());
    }
}
