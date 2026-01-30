package com.gdg.z_meet.domain.booth.controller;

import com.gdg.z_meet.domain.booth.converter.BoothConverter;
import com.gdg.z_meet.domain.booth.dto.BoothResponseDTO;
import com.gdg.z_meet.domain.booth.entity.Place;
import com.gdg.z_meet.domain.booth.service.BoothQueryService;
import com.gdg.z_meet.global.response.Response;
import com.gdg.z_meet.domain.booth.dto.BoothRequestDTO;
import com.gdg.z_meet.domain.booth.entity.Club;
import com.gdg.z_meet.domain.booth.service.BoothCommandService;
import com.gdg.z_meet.global.validation.annotation.ValidEnum;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.*;

@RestController
@RequiredArgsConstructor
@RequestMapping("/api/booths")
@Tag(name = "Booth")
@Validated
public class BoothController {

    private final BoothCommandService boothCommandService;
    private final BoothQueryService boothQueryService;

    @Operation(summary = "(테스트용)부스 등록")
    @PostMapping
    public Response<BoothResponseDTO.CreateClubDTO> createClub(@RequestBody @Valid BoothRequestDTO.CreateClubDTO request) {

        BoothResponseDTO.CreateClubDTO response = boothCommandService.createClub(request);

        return Response.ok(response);
    }

    @Operation(summary = "부스배치도 조회", description = "장소별로 부스를 조회합니다.")
    @GetMapping("/{place}")
    public Response<BoothResponseDTO.GetAllClubDTO> getAllClub(@PathVariable @ValidEnum(enumClass = Place.class) String place) {

        BoothResponseDTO.GetAllClubDTO response = boothQueryService.getAllClub(place);

        return Response.ok(response);
    }

    @Operation(summary = "부스 상세 조회")
    @GetMapping("/detail/{clubId}")
    public Response<BoothResponseDTO.GetClubDTO> getClub(@PathVariable Long clubId) {

        BoothResponseDTO.GetClubDTO response = boothQueryService.getClub(clubId);

        return Response.ok(response);
    }
}