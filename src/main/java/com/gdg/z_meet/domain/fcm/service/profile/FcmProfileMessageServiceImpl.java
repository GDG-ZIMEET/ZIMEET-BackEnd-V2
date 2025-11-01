package com.gdg.z_meet.domain.fcm.service.profile;

import com.gdg.z_meet.domain.fcm.service.producer.FcmMessageProducer;
import com.gdg.z_meet.domain.meeting.entity.Team;
import com.gdg.z_meet.domain.meeting.entity.UserTeam;
import com.gdg.z_meet.domain.meeting.repository.TeamRepository;
import com.gdg.z_meet.domain.meeting.repository.UserTeamRepository;
import com.gdg.z_meet.domain.user.entity.UserProfile;
import com.gdg.z_meet.domain.user.repository.UserProfileRepository;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.List;
import java.util.Map;
import java.util.TreeMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class FcmProfileMessageServiceImpl implements FcmProfileMessageService {

    private final FcmMessageProducer fcmMessageProducer;
    private final UserTeamRepository userTeamRepository;
    private final UserProfileRepository userProfileRepository;
    private final TeamRepository teamRepository;

    /**
     *  프로필 조회 API 호출 시 실행
     */
    @Override
    public void messagingProfileViewOneOneUsers(List<UserProfile> profiles) {
        Map<Integer, String> messageTitles = new TreeMap<>(Map.of(
                10, "🥳 내 프로필을 10명이나 봤어요! 🎉 인기 폭발 시작이에요!",
                50, "🔥 벌써 50명이 다녀갔어요! 대세는 역시 나, 지금 확인해보세요!",
                100, "💯 무려 100명이 당신을 봤어요! 관심 폭주 중이에요, 놓치지 마세요!",
                500, "🌟 500명 돌파! 이 정도면 거의 스타 등장이죠? 지금 확인해봐요!",
                1000, "🏆 1000명 초과 달성! ZI밋에서 당신의 인기가 뜨겁게 타오르고 있어요!"
        ));
        String body = "인기있는 당신!! 어떤 사람들이 ZI밋에 있는지 확인해볼까요?🔥";

        for (UserProfile profile : profiles) {
            int viewCount = profile.getViewCount();
            int lastNotified = profile.getLastNotified();
            Long userId = profile.getUser().getId();

            int maxMilestone = -1;
            String titleToSend = null;

            for (Map.Entry<Integer, String> entry : messageTitles.entrySet()) {
                int milestone = entry.getKey();
                if (viewCount >= milestone && milestone > lastNotified) {
                    if (milestone > maxMilestone) {
                        maxMilestone = milestone;
                        titleToSend = entry.getValue();
                    }
                }
            }

            if (titleToSend != null) {    // 중복 발송을 막기 위함
                // RabbitMQ를 통한 비동기 FCM 메시지 전송
                fcmMessageProducer.sendSingleMessage(userId, titleToSend, body);
                // 큐에 성공적으로 전송되었으므로 마지막 알림 milestone 기록
                profile.setLastNotified(maxMilestone);
                userProfileRepository.save(profile);
                log.info("프로필 조회 수 알림 메시지를 큐에 전송했습니다 - userId: {}, milestone: {}", userId, maxMilestone);
            }
        }
    }


    @Override
    public void messagingProfileViewTwoTwoUsers(List<Team> teams) {
        Map<Integer, String> messageTitles = new TreeMap<>(Map.of(
                10, "🥳 우리 팀 프로필을 10명이나 봤어요! 🎉 인기 폭발 시작이에요!",
                50, "🔥 벌써 50명이 다녀갔어요! 대세는 역시 우리 팀, 지금 확인해보세요!",
                100, "💯 무려 100명이 우리 팀을 봤어요! 관심 폭주 중이에요, 놓치지 마세요!",
                500, "🌟 500명 돌파! 이 정도면 거의 스타 등장이죠? 지금 확인해봐요!",
                1000, "🏆 1000명 초과 달성! ZI밋에서 우리 팀의 인기가 뜨겁게 타오르고 있어요!"
        ));
        String body = "인기있는 우리 팀!! 어떤 팀들이 ZI밋에 있는지 확인해볼까요?🔥";


        for (Team team : teams) {
            int viewCount = team.getViewCount();
            int lastNotified = team.getLastNotified();

            int maxMilestone = -1;
            String titleToSend = null;

            for (Map.Entry<Integer, String> entry : messageTitles.entrySet()) {
                int milestone = entry.getKey();
                if (viewCount >= milestone && milestone > lastNotified) {
                    if (milestone > maxMilestone) {
                        maxMilestone = milestone;
                        titleToSend = entry.getValue();
                    }
                }
            }

            if (titleToSend != null) {   // 중복 발송을 막기 위함
                List<UserTeam> userTeams = userTeamRepository.findAllByTeam(team);
                // RabbitMQ를 통한 비동기 FCM 메시지 전송
                for (UserTeam userTeam : userTeams) {
                    Long userId = userTeam.getUser().getId();
                    fcmMessageProducer.sendSingleMessage(userId, titleToSend, body);
                }
                // 큐에 성공적으로 전송되었으므로 마지막 알림 milestone 기록
                team.setLastNotified(maxMilestone);
                teamRepository.save(team);
                log.debug("팀 조회 수 알림 메시지를 큐에 전송했습니다 - teamId: {}, milestone: {}, members: {}",
                        team.getId(), maxMilestone, userTeams.size());
            }
        }
    }
}
