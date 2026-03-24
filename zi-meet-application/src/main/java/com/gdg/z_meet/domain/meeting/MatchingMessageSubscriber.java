package com.gdg.z_meet.domain.meeting;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.gdg.z_meet.domain.meeting.dto.RandomResponseDTO;
import lombok.RequiredArgsConstructor;
import org.springframework.messaging.simp.SimpMessagingTemplate;
import org.springframework.stereotype.Component;

@Component
@RequiredArgsConstructor
public class MatchingMessageSubscriber {

    private final SimpMessagingTemplate messagingTemplate;
    private final ObjectMapper objectMapper;

    public void handleMessage(String message) {

        try {
            if (message.startsWith("\"") && message.endsWith("\"")) {
                message = message.substring(1, message.length() - 1);
                message = message.replace("\\\"", "\"");
            }
            RandomResponseDTO.MatchingDTO matchingDTO =
                    objectMapper.readValue(message, RandomResponseDTO.MatchingDTO.class);

            String destination = "/topic/matching/" + matchingDTO.getGroupId();
            messagingTemplate.convertAndSend(destination, matchingDTO);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}