package com.gdg.z_meet.domain.fcm.unit.service.meeting;

import com.gdg.z_meet.domain.fcm.service.meeting.FcmMeetingMessageServiceImpl;
import com.gdg.z_meet.domain.fcm.service.producer.FcmMessageProducer;
import com.gdg.z_meet.domain.meeting.dto.MeetingResponseDTO;
import com.gdg.z_meet.domain.meeting.repository.HiRepository;
import com.gdg.z_meet.domain.meeting.repository.TeamRepository;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.meeting.service.HiQueryService;
import com.gdg.z_meet.domain.user.entity.User;
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

    @Test
    @DisplayName("2:2 팀 미생성 사용자에게 알림")
    void 팀미생성사용자_알림() {
        User user1 = User.builder().id(1L).fcmSendTwoTwo(false).build();
        User user2 = User.builder().id(2L).fcmSendTwoTwo(false).build();

        when(teamRepository.findUsersNotInTwoToTwoTeam(any())).thenReturn(List.of(user1, user2));

        fcmMeetingMessageService.messagingNoneMeetingTwoTwoUsers();

        verify(fcmMessageProducer, times(2)).sendSingleMessage(
                any(Long.class), 
                eq("👀 아직 2대2 팀을 만들지 않으셨네요!"), 
                any()
        );
        verify(userRepository, times(2)).save(any(User.class));
    }

    @Test
    @DisplayName("개별 사용자에게 하이 알림")
    void 개별사용자_하이알림() {
        Long targetUserId = 1L;

        fcmMeetingMessageService.messagingHiToUser(targetUserId);

        verify(fcmMessageProducer).sendSingleMessage(
                eq(targetUserId), 
                eq("❤️나에게 하이가 도착했어요! 💌"), 
                any()
        );
    }

    @Test
    @DisplayName("null 사용자에게 하이 알림 - 무시")
    void null사용자_하이알림_무시() {
        Long targetUserId = null;

        fcmMeetingMessageService.messagingHiToUser(targetUserId);

        verify(fcmMessageProducer, never()).sendSingleMessage(any(), any(), any());
    }

    @Test
    @DisplayName("팀에게 하이 알림")
    void 팀_하이알림() {
        Long targetTeamId = 1L;
        List<Long> userIds = List.of(1L, 2L, 3L);

        when(userTeamRepository.findUserIdsByTeamId(targetTeamId)).thenReturn(userIds);

        fcmMeetingMessageService.messagingHiToTeam(targetTeamId);

        verify(userTeamRepository).findUserIdsByTeamId(targetTeamId);
        verify(fcmMessageProducer, times(3)).sendSingleMessage(
                any(Long.class), 
                eq("❤️우리 팀에게 하이가 도착했어요! 💌"), 
                any()
        );
    }

    @Test
    @DisplayName("하이 미확인 개별 사용자에게 알림")
    void 하이미확인_개별사용자_알림() {
        List<Long> userIds = List.of(1L, 2L);
        List<MeetingResponseDTO.hiListDto> pendingHiList = List.of(
                new MeetingResponseDTO.hiListDto()
        );

        when(hiRepository.findUserIdsToNotGetHi()).thenReturn(userIds);
        when(hiQueryService.checkHiList(any(), eq("Receive"))).thenReturn(pendingHiList);

        fcmMeetingMessageService.messagingNotAcceptHiToUser();

        verify(hiRepository).findUserIdsToNotGetHi();
        verify(fcmMessageProducer, times(2)).sendSingleMessage(
                any(Long.class), 
                eq("혹시 받은 하이를 잊으셨나요? 🥺"), 
                any()
        );
    }
}
