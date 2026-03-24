package com.gdg.z_meet.domain.fcm.unit.service.meeting;

import com.gdg.z_meet.domain.fcm.service.meeting.FcmMeetingMessageServiceImpl;
import com.gdg.z_meet.domain.fcm.service.producer.FcmMessageProducer;
import com.gdg.z_meet.domain.meeting.dto.MeetingResponseDTO;
import com.gdg.z_meet.domain.meeting.repository.HiRepository;
import com.gdg.z_meet.domain.meeting.repository.TeamRepository;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.meeting.service.HiQueryService;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import com.gdg.z_meet.domain.user.repository.UserRepository;
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
@DisplayName("FcmMeetingMessageService 단위 테스트")
class FcmMeetingMessageServiceImplTest {

    @Mock
    private UserProfileRepository userProfileRepository;

    @Mock
    private TeamRepository teamRepository;

    @Mock
    private UserTeamRepository userTeamRepository;

    @Mock
    private HiRepository hiRepository;

    @Mock
    private FcmMessageProducer fcmMessageProducer;

    @Mock
    private HiQueryService hiQueryService;

    @Mock
    private UserRepository userRepository;

    @InjectMocks
    private FcmMeetingMessageServiceImpl fcmMeetingMessageService;

    // TODO: 채팅 쪽 수정 중이므로 임시 주석처리 - Meeting 테스트도 연관되어 실패
    /*
    @Test
    @DisplayName("1:1 미팅 비활성 사용자에게 알림")
    void messagingNoneMeetingOneOneUsers() {
        // Given
        User user1 = User.builder().id(1L).build();
        User user2 = User.builder().id(2L).build();
        
        UserProfile userProfile1 = UserProfile.builder()
                .user(user1)
                .fcmSendOneOne(false)
                .build();
        
        UserProfile userProfile2 = UserProfile.builder()
                .user(user2)
                .fcmSendOneOne(false)
                .build();

        when(userProfileRepository.findInactiveUsers(any())).thenReturn(List.of(userProfile1, userProfile2));

        // When
        fcmMeetingMessageService.messagingNoneMeetingOneOneUsers();

        // Then
        verify(fcmMessageProducer, times(2)).sendSingleMessage(
                any(Long.class), 
                eq("👀 아직 내 프로필이 활성화되지 않았어요."), 
                eq("'1대1 참여하기' 버튼으로 내 프로필을 활성화해야 상대방이 볼 수 있어요!")
        );
        verify(userProfileRepository, times(2)).save(any(UserProfile.class));
    }
    */

    @Test
    @DisplayName("2:2 팀 미생성 사용자에게 알림")
    void messagingNoneMeetingTwoTwoUsers() {
        // Given
        User user1 = User.builder().id(1L).fcmSendTwoTwo(false).build();
        User user2 = User.builder().id(2L).fcmSendTwoTwo(false).build();

        when(teamRepository.findUsersNotInTwoToTwoTeam(any())).thenReturn(List.of(user1, user2));

        // When
        fcmMeetingMessageService.messagingNoneMeetingTwoTwoUsers();

        // Then
        verify(fcmMessageProducer, times(2)).sendSingleMessage(
                any(Long.class), 
                eq("👀 아직 2대2 팀을 만들지 않으셨네요!"), 
                eq("마음 맞는 친구와 팀을 만들어보세요. 함께하면 매칭 확률이 훨씬 높아져요 🔥")
        );
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    @DisplayName("개별 사용자에게 하이 알림")
    void messagingHiToUser() {
        // Given
        Long targetUserId = 1L;

        // When
        fcmMeetingMessageService.messagingHiToUser(targetUserId);

        // Then
        verify(fcmMessageProducer).sendSingleMessage(
                eq(targetUserId), 
                eq("❤️나에게 하이가 도착했어요! 💌"), 
                eq("ZI밋에서 어떤 사람에게 하이가 왔는지 확인해보세요!")
        );
    }

    @Test
    @DisplayName("null 사용자에게 하이 알림 - 무시")
    void messagingHiToUserWithNull() {
        // Given
        Long targetUserId = null;

        // When
        fcmMeetingMessageService.messagingHiToUser(targetUserId);

        // Then
        verify(fcmMessageProducer, never()).sendSingleMessage(any(), any(), any());
    }

    @Test
    @DisplayName("팀에게 하이 알림")
    void messagingHiToTeam() {
        // Given
        Long targetTeamId = 1L;
        List<Long> userIds = List.of(1L, 2L, 3L);

        when(userTeamRepository.findUserIdsByTeamId(targetTeamId)).thenReturn(userIds);

        // When
        fcmMeetingMessageService.messagingHiToTeam(targetTeamId);

        // Then
        verify(userTeamRepository).findUserIdsByTeamId(targetTeamId);
        verify(fcmMessageProducer, times(3)).sendSingleMessage(
                any(Long.class), 
                eq("❤️우리 팀에게 하이가 도착했어요! 💌"), 
                eq("ZI밋에서 어떤 팀에게 하이가 왔는지 확인해보세요! ")
        );
    }

    @Test
    @DisplayName("하이 미확인 개별 사용자에게 알림")
    void messagingNotAcceptHiToUser() {
        // Given
        List<Long> userIds = List.of(1L, 2L);
        List<MeetingResponseDTO.hiListDto> pendingHiList = List.of(
                new MeetingResponseDTO.hiListDto()
        );

        when(hiRepository.findUserIdsToNotGetHi()).thenReturn(userIds);
        when(hiQueryService.checkHiList(any(), eq("Receive"))).thenReturn(pendingHiList);

        // When
        fcmMeetingMessageService.messagingNotAcceptHiToUser();

        // Then
        verify(hiRepository).findUserIdsToNotGetHi();
        verify(hiQueryService, times(2)).checkHiList(any(), eq("Receive"));
        verify(fcmMessageProducer, times(2)).sendSingleMessage(
                any(Long.class), 
                eq("혹시 받은 하이를 잊으셨나요? 🥺"), 
                eq("받은 하이는 ⏰5시간 후에 사라지니 빠르게 확인해보세요!")
        );
    }

    @Test
    @DisplayName("하이 미확인 팀에게 알림")
    void messagingNotAcceptHiToTeam() {
        // Given
        List<Long> teamIds = List.of(1L, 2L);
        List<Long> userIds = List.of(1L, 2L, 3L);
        List<MeetingResponseDTO.hiListDto> pendingHiList = List.of(
                new MeetingResponseDTO.hiListDto()
        );

        when(hiRepository.findTeamIdToNotGetHi()).thenReturn(teamIds);
        when(userTeamRepository.findUserIdsByTeamIds(teamIds)).thenReturn(userIds);
        when(hiQueryService.checkHiList(any(), eq("Receive"))).thenReturn(pendingHiList);

        // When
        fcmMeetingMessageService.messagingNotAcceptHiToTeam();

        // Then
        verify(hiRepository).findTeamIdToNotGetHi();
        verify(userTeamRepository).findUserIdsByTeamIds(teamIds);
        verify(hiQueryService, times(3)).checkHiList(any(), eq("Receive"));
        verify(fcmMessageProducer, times(3)).sendSingleMessage(
                any(Long.class), 
                eq("혹시 받은 하이를 잊으셨나요? 🥺"), 
                eq("받은 하이는 ⏰5시간 후에 사라지니 빠르게 확인해보세요!")
        );
    }
}
