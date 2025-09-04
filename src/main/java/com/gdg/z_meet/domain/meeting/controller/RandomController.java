package com.gdg.z_meet.domain.meeting.controller;

import com.gdg.z_meet.domain.meeting.dto.RandomResponseDTO;
import com.gdg.z_meet.domain.meeting.service.RandomCommandService;
import com.gdg.z_meet.domain.meeting.service.RandomQueryService;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.jwt.JwtUtil;
import com.gdg.z_meet.global.response.Response;
import com.gdg.z_meet.global.security.AuthUser;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.messaging.handler.annotation.Header;
import org.springframework.messaging.handler.annotation.MessageMapping;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

import java.util.HashMap;
import java.util.Map;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/random")
@Tag(name = "RandomMeeting")
@Validated
@Slf4j
public class RandomController {

    private final JwtUtil jwtUtil;
    private final RandomCommandService randomCommandService;
    private final RandomQueryService randomQueryService;

    @Operation(summary = "남은 티켓 개수")
    @GetMapping("/ticket")
    public Response<RandomResponseDTO.GetTicketDTO> getTicket(@AuthUser Long userId) {

        RandomResponseDTO.GetTicketDTO response = randomQueryService.getTicket(userId);

        return Response.ok(response);
    }

    @Operation(summary = "랜덤 매칭 참여하기")
    @MessageMapping("/matching/join")
    public void joinMatching(@Header("Authorization") String token) {

        Long userId = jwtUtil.extractUserIdFromToken(token);
        try {
            randomCommandService.joinMatching(userId);
            Response.ok();
        } catch (BusinessException ex) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", ex.getReason().getStatus());
            errorResponse.put("code", ex.getCode());
            errorResponse.put("message", ex.getMessage());

            log.info("errorResponse: {}", errorResponse);
            throw ex;
        }
    }

    @Operation(summary = "랜덤 매칭 취소하기")
    @MessageMapping("/matching/cancel")
    public void cancelMatching(@Header("Authorization") String token) {

        Long userId = jwtUtil.extractUserIdFromToken(token);
        try {
            randomCommandService.cancelMatching(userId);
        } catch (BusinessException ex) {
            Map<String, Object> errorResponse = new HashMap<>();
            errorResponse.put("status", ex.getReason().getStatus());
            errorResponse.put("code", ex.getCode());
            errorResponse.put("message", ex.getMessage());

            log.info("errorResponse: {}", errorResponse);
        }
    }

    @Operation(summary = "매칭 참여 후 현황 조회")
    @GetMapping("/matching")
    public Response<RandomResponseDTO.MatchingDTO> getMatching(@AuthUser Long userId) {

        RandomResponseDTO.MatchingDTO response = randomQueryService.getMatching(userId);

        return Response.ok(response);
    }
}