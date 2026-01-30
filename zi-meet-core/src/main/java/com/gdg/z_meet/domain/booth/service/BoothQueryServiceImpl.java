package com.gdg.z_meet.domain.booth.service;

import com.gdg.z_meet.domain.booth.converter.BoothConverter;
import com.gdg.z_meet.domain.booth.dto.BoothResponseDTO;
import com.gdg.z_meet.domain.booth.entity.Club;
import com.gdg.z_meet.domain.booth.entity.Item;
import com.gdg.z_meet.domain.booth.entity.Place;
import com.gdg.z_meet.domain.booth.repository.ClubRepository;
import com.gdg.z_meet.domain.booth.repository.ItemRepository;
import com.gdg.z_meet.global.exception.BusinessException;
import com.gdg.z_meet.global.response.Code;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;

import java.util.List;

@Service
@RequiredArgsConstructor
public class BoothQueryServiceImpl implements BoothQueryService {

    private final ClubRepository clubRepository;
    private final ItemRepository itemRepository;

    @Override
    public BoothResponseDTO.GetAllClubDTO getAllClub(String place) {

        List<Club> clubList = clubRepository.findByPlace(Place.valueOf(place));
        return BoothConverter.toGetAllClubDTO(clubList);
    }

    @Override
    public BoothResponseDTO.GetClubDTO getClub(Long clubId) {

        List<Item> itemList = itemRepository.findByClubId(clubId);
        Club club = clubRepository.findById(clubId).orElseThrow(() -> new BusinessException(Code.CLUB_NOT_FOUND));
        return BoothConverter.toGetClubDTO(itemList, club);
    }
}