package com.gdg.z_meet.domain.meeting.service;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdg.z_meet.domain.chat.service.ChatRoomCommandService;
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
import com.gdg.z_meet.domain.user.entity.enums.Gender;
import com.gdg.z_meet.domain.user.repository.UserRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Optional;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class RandomCommandServiceImpl implements RandomCommandService {

    @Qualifier("matchingRedisTemplate")
    private final RedisTemplate<String, String> matchingRedisTemplate;

    @Autowired
    private final ObjectMapper objectMapper;

    private final MatchingRepository matchingRepository;
    private final UserMatchingRepository userMatchingRepository;
    private final MatchingQueueRepository matchingQueueRepository;
    private final UserRepository userRepository;
    private final ChatRoomCommandService chatRoomCommandService;

    @Autowired
    private MatchingLockService matchingLockService;


    @Override
    @Transactional
    public void joinMatching(Long userId) {

        User user = validateUser(userId);
        Gender gender = user.getUserProfile().getGender();

        String groupId = findJoinableGroupId(gender)
                .orElse(UUID.randomUUID().toString());

        MatchingQueue queue = MatchingQueue.builder()
                .groupId(groupId)
                .gender(gender)
                .user(user)
                .build();
        matchingQueueRepository.save(queue);

        List<MatchingQueue> queueList = matchingQueueRepository.findByGroupIdWithLock(groupId); // 매칭 전에 락 획득!

        boolean isComplete = isMatchingComplete(queueList);
        messageMatching(groupId, queueList, isComplete);
        if (isComplete) createMatching(queueList);
    }

    private User validateUser(Long userId) {

        User user = userRepository.findByIdWithProfile(userId);

        if (user.getUserProfile().getTicket() == 0) {
            throw new BusinessException(Code.TICKET_LIMIT_EXCEEDED);
        }
        if (matchingQueueRepository.existsWaitingByUserId(userId)) {
            throw new BusinessException(Code.MATCHING_ALREADY_EXIST);
        }
        user.getUserProfile().decreaseTicket(1);

        return user;
    }

    private Optional<String> findJoinableGroupId(Gender gender) {
        List<String> groupIds = matchingQueueRepository.findAllJoinableGroupIds();

        for (String groupId : groupIds) {
            List<MatchingQueue> queueList = matchingQueueRepository.findByGroupIdWithLock(groupId);
            long genderCount = queueList.stream().filter(q -> q.getGender() == gender).count();

            if (queueList.size() < 4 && genderCount < 2) {
                return Optional.of(groupId);
            }
        }

        return Optional.empty();
    }

    private boolean isMatchingComplete(List<MatchingQueue> queueList) {

        long male = queueList.stream().filter(q -> q.getGender() == Gender.MALE).count();
        long female = queueList.stream().filter(q -> q.getGender() == Gender.FEMALE).count();

        return queueList.size() == 4 && male == 2 && female == 2;
    }

    @Override
    @Transactional
    public void cancelMatching(Long userId) {

        MatchingQueue queue = matchingQueueRepository.findWaitingByUserIdWithLock(userId)
                .orElseThrow(() -> new BusinessException(Code.MATCHING_NOT_FOUND));

        String groupId = queue.getGroupId();

        matchingQueueRepository.delete(queue);

        User user = userRepository.findByIdWithProfile(userId);
        user.getUserProfile().increaseTicket(1);

        List<MatchingQueue> queueList = matchingQueueRepository.findByGroupIdWithLock(groupId);
        boolean isComplete = isMatchingComplete(queueList);

        messageMatching(queue.getGroupId(), queueList, isComplete);
    }

    private void messageMatching(String groupId, List<MatchingQueue> queueList, boolean isComplete) {

        List<User> users = queueList.stream()
                .map(MatchingQueue::getUser)
                .collect(Collectors.toList());

        MatchingStatus matchingStatus = isComplete ? MatchingStatus.COMPLETE : MatchingStatus.WAITING;

        RandomResponseDTO.MatchingDTO matchingDTO = RandomConverter.toMatchingDTO(groupId, users, matchingStatus);

        try {
            String jsonMessage = objectMapper.writeValueAsString(matchingDTO);
            String channel = "matching." + groupId;
            matchingRedisTemplate.convertAndSend(channel, jsonMessage);
        } catch (JsonProcessingException e) {
            throw new BusinessException(Code.MESSAGE_PROCESSING_ERROR);
        }
    }

    @Transactional
    public void createMatching(List<MatchingQueue> queueList) {

        Matching matching = matchingRepository.save(Matching.builder().build());

        queueList.forEach(queue -> {
            userMatchingRepository.save(UserMatching.builder()
                    .user(queue.getUser())
                    .matching(matching)
                    .build());
            queue.setComplete();
        });

        List<Long> userIds = queueList.stream()
                .map(q -> q.getUser().getId())
                .collect(Collectors.toList());
        chatRoomCommandService.addRandomUserJoinChat(userIds);
    }
}