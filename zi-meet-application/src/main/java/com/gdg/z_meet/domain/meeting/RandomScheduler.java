package com.gdg.z_meet.domain.meeting;

import com.gdg.z_meet.domain.meeting.repository.MatchingQueueRepository;
import jakarta.transaction.Transactional;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;

@Slf4j
@Component
@RequiredArgsConstructor
public class RandomScheduler {

    private final MatchingQueueRepository matchingQueueRepository;

    @Scheduled(cron = "0 0 4 * * ?") // 매일 4시마다 실행
    @Transactional
    public void cleanCompleteMatchingQueues() {

        try {
            LocalDateTime threshold = LocalDateTime.now().minusHours(12); // 매칭 후 12시간 지나야 삭제 가능
            matchingQueueRepository.deleteByUpdatedBefore(threshold);
        } catch (Exception e) {
            log.error("매칭 큐 정리 중 오류 발생", e);
        }
    }
}
