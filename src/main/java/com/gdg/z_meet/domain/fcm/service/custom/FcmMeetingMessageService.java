package com.gdg.z_meet.domain.fcm.service.custom;

import com.gdg.z_meet.domain.fcm.service.FcmMessageClient;
import com.gdg.z_meet.domain.meeting.dto.MeetingResponseDTO;
import com.gdg.z_meet.domain.meeting.entity.enums.Event;
import com.gdg.z_meet.domain.meeting.repository.HiRepository;
import com.gdg.z_meet.domain.meeting.repository.TeamRepository;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.meeting.service.HiQueryService;
import com.gdg.z_meet.domain.meeting.service.HiQueryServiceImpl;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;


import java.time.LocalDateTime;
import java.util.List;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmMeetingMessageService {

    private final UserProfileRepository userProfileRepository;
    private final TeamRepository teamRepository;
    private final UserTeamRepository userTeamRepository;
    private final HiRepository hiRepository;


    private final FcmMessageClient fcmMessageClient;
    private final HiQueryService hiQueryService;
    private final UserRepository userRepository;

    @Scheduled(fixedRate = 3600000)      // 1시간마다 실행
    public void messagingNoneMeetingOneOneUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        List<UserProfile> users = userProfileRepository.findInactiveUsers(threshold);

        String title = "👀 아직 내 프로필이 활성화되지 않았어요.";
        String body = "‘1대1 참여하기’ 버튼으로 내 프로필을 활성화해야 상대방이 볼 수 있어요!";

        for (UserProfile user : users) {
            boolean success = fcmMessageClient.sendFcmMessage(user.getId(), title, body);

            if (success) {
                user.setFcmSendOneOne(true);
                userProfileRepository.save(user);
            }
//            else {
//                log.warn("1:1 프로필 관련 FCM 메시지 전송 실패 - userId={}", user.getId());
//            }
        }
    }

    @Scheduled(fixedRate = 3600000)      // 1시간마다 실행
    public void messagingNoneMeetingTwoTwoUsers() {
        LocalDateTime threshold = LocalDateTime.now().minusHours(24);
        List<User> users = teamRepository.findUsersNotInTwoToTwoTeam(threshold);

        String title = "👀 아직 2대2 팀을 만들지 않으셨네요!";
        String body = "마음 맞는 친구와 팀을 만들어보세요. 함께하면 매칭 확률이 훨씬 높아져요 🔥";

        for (User user : users) {

            boolean success = fcmMessageClient.sendFcmMessage(user.getId(), title, body);

            if (success) {
                user.setFcmSendTwoTwo(true);
                userRepository.save(user);
            }
//            else {
//                log.warn("2:2 팀매칭 관련 FCM 메시지 실패 처리됨 - userId={}", user.getId());
//            }
        }
    }

    ////////////////

    // 하이 보내기 호출 시, 실행되므로 스케줄링 적용 하지 않음
    public void messagingHiToUser(Long targetUserId) {
        if (targetUserId == null) { return ;}

        String title = "❤️나에게 하이가 도착했어요! 💌";
        String body = "ZI밋에서 어떤 사람에게 하이가 왔는지 확인해보세요!";

        boolean success = fcmMessageClient.sendFcmMessage(targetUserId, title, body);
        if (!success) {
            log.warn("FCM 하이 메시지 실패 처리됨 - targetUserId={}", targetUserId);
        }
    }


    @Scheduled(fixedRate = 600000)    // 10분 마다
    public void messagingNotAcceptHiToUser() {

        List<Long> userIds = hiRepository.findUserIdsToNotGetHi();
        if (userIds.isEmpty()) return;

        String title = "혹시 받은 하이를 잊으셨나요? 🥺";
        String body = "받은 하이는 ⏰5시간 후에 사라지니 빠르게 확인해보세요!";

        for (Long userId : userIds) {
            List<MeetingResponseDTO.hiListDto> pendingHiList =  hiQueryService.checkHiList(userId, "Receive");

            if (pendingHiList.isEmpty()) {
                continue;       // 받은 하이가 없음 → FCM 전송 스킵
            }

            boolean success = fcmMessageClient.sendFcmMessage(userId, title, body);
            if(!success) {
                log.warn("FCM 받은 하이 여부 메시지 전송 실패 userId: {}", userId);
            }
        }
    }

    ////////////////

    // 하이 보내기 호출 시, 실행되므로 스케줄링 적용 하지 않음
    public void messagingHiToTeam(Long targetTeamId) {
        if (targetTeamId == null) { return ;}

        List<Long> userIds = userTeamRepository.findUserIdsByTeamId(targetTeamId);

        String title = "❤️우리 팀에게 하이가 도착했어요! 💌";
        String body = "ZI밋에서 어떤 팀에게 하이가 왔는지 확인해보세요! ";

        for (Long userId : userIds) {
            boolean success = fcmMessageClient.sendFcmMessage(userId, title, body);
            if(!success) {
                log.warn("FCM 하이(팀) 메시지 전송 실패 - userId: {}", userId);
            }
        }
    }

    @Scheduled(fixedRate = 600000)  // 10분 마다
    public void messagingNotAcceptHiToTeam() {

        List<Long> teamIds = hiRepository.findTeamIdToNotGetHi();
        if (teamIds.isEmpty()) return;

        List<Long> userIds = userTeamRepository.findUserIdsByTeamIds(teamIds);

        String title = "혹시 받은 하이를 잊으셨나요? 🥺";
        String body = "받은 하이는 ⏰5시간 후에 사라지니 빠르게 확인해보세요!";

        for (Long userId : userIds) {
            List<MeetingResponseDTO.hiListDto> pendingHiList = hiQueryService.checkHiList(userId, "Receive");

            if (pendingHiList.isEmpty()) {
                continue;       // 받은 하이가 없음 → FCM 전송 스킵
            }

            boolean success = fcmMessageClient.sendFcmMessage(userId, title, body);
            if(!success) {
                log.warn(" FCM 받은 하이 여부 메시지 전송 실패 userId: {}", userId);
            }
        }
    }
}
