package com.gdg.z_meet.domain.event.controller;

import com.gdg.z_meet.domain.event.Product;
import com.gdg.z_meet.domain.event.dto.EventResponseDTO;
import com.gdg.z_meet.domain.event.service.EventService;
import com.gdg.z_meet.domain.meeting.dto.MeetingResponseDTO;
import com.gdg.z_meet.domain.user.dto.UserRes;
import com.gdg.z_meet.global.response.Response;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/event")
@Tag(name = "Event")
@Validated
public class EventController {

    private final EventService eventService;

    @Operation(summary = "팀 삭제 기회 추가")
    @PatchMapping("/leftDelete")
    public Response<MeetingResponseDTO.GetMyDeleteDTO> patchMyDelete(@RequestParam(name = "name") String name, @RequestParam(name = "studentNumber") String studentNumber) {

        MeetingResponseDTO.GetMyDeleteDTO response = eventService.patchMyDelete(name, studentNumber);

        return Response.ok(response);
    }

    @Operation(summary = "ZI밋+ 등급 등록")
    @PatchMapping("/level")
    public Response<UserRes.GetLevelDTO> patchLevel(@RequestParam(name = "name") String name, @RequestParam(name = "studentNumber") String studentNumber) {

        UserRes.GetLevelDTO response = eventService.patchLevel(name, studentNumber);

        return Response.ok(response);
    }

    @Operation(summary = "인증뱃지 추가")
    @PatchMapping("/verification")
    public Response<MeetingResponseDTO.GetVerificationDTO> patchVerification(@RequestParam(name = "name") String name, @RequestParam(name = "studentNumber") String studentNumber) {

        MeetingResponseDTO.GetVerificationDTO response = eventService.patchVerification(name, studentNumber);

        return Response.ok(response);
    }

    @Operation(summary = "결제")
    @PatchMapping("/pay")
    public Response<EventResponseDTO.GetPayDTO> patchPay(@RequestParam(name = "name") String name,
                                                         @RequestParam(name = "studentNumber") String studentNumber,
                                                         @RequestParam(name = "product") Product product) {

        EventResponseDTO.GetPayDTO response = eventService.patchPay(name, studentNumber, product);

        return Response.ok(response);
    }
}
