package com.gdg.z_meet.domain.meeting.service;

import com.gdg.z_meet.domain.meeting.converter.RandomConverter;
import com.gdg.z_meet.domain.meeting.dto.RandomResponseDTO;
import com.gdg.z_meet.domain.meeting.entity.Matching;
import com.gdg.z_meet.domain.meeting.entity.MatchingQueue;
import com.gdg.z_meet.domain.meeting.entity.UserMatching;
import com.gdg.z_meet.domain.meeting.entity.enums.MatchingStatus;
import com.gdg.z_meet.domain.meeting.repository.MatchingQueueRepository;
import com.gdg.z_meet.domain.meeting.repository.MatchingRepository;
import com.gdg.z_meet.domain.meeting.repository.UserMatchingRepository;
import com.gdg.z_meet.domain.user.entity.User;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RandomQueryServiceImpl implements RandomQueryService {

    private final MatchingRepository matchingRepository;
    private final UserMatchingRepository userMatchingRepository;
    private final UserRepository userRepository;
    private final MatchingQueueRepository matchingQueueRepository;

    @Override
    @Transactional(readOnly = true)
    public RandomResponseDTO.GetTicketDTO getTicket(Long userId) {

        User user = userRepository.findByIdWithProfile(userId);

        return RandomConverter.toGetTicketDTO(user);
    }

    @Override
    @Transactional
    public RandomResponseDTO.MatchingDTO getMatching(Long userId) {

        MatchingQueue queue = matchingQueueRepository.findByUserIdWithLock(userId)
                .stream().findFirst()
                .orElseThrow(() -> new BusinessException(Code.MATCHING_NOT_FOUND));

        String groupId = queue.getGroupId();

        List<MatchingQueue> queueList = matchingQueueRepository.findByGroupIdWithLock(groupId);

        List<User> users = queueList.stream()
                .map(MatchingQueue::getUser)
                .collect(Collectors.toList());

        MatchingStatus matchingStatus = queueList.size() == 4 ? MatchingStatus.COMPLETE : MatchingStatus.WAITING;

        return RandomConverter.toMatchingDTO(groupId, users, matchingStatus);
    }
}