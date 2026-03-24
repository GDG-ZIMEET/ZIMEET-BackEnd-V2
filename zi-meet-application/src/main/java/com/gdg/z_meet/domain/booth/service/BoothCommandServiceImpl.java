package com.gdg.z_meet.domain.booth.service;

import com.gdg.z_meet.domain.booth.converter.BoothConverter;
import com.gdg.z_meet.domain.booth.dto.BoothRequestDTO;
import com.gdg.z_meet.domain.booth.dto.BoothResponseDTO;
import com.gdg.z_meet.domain.booth.entity.Club;
import com.gdg.z_meet.domain.booth.entity.Item;
import com.gdg.z_meet.domain.booth.repository.ClubRepository;
import com.gdg.z_meet.domain.booth.repository.ItemRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class BoothCommandServiceImpl implements BoothCommandService {

    private final ClubRepository clubRepository;
    private final ItemRepository itemRepository;

    @Override
    @Transactional
    public BoothResponseDTO.CreateClubDTO createClub(BoothRequestDTO.CreateClubDTO request) {

        Club club = BoothConverter.toClub(request);
        Club newClub = clubRepository.save(club);

        List<Item> itemList = request.getItemList().stream()
                .map(item -> BoothConverter.toItem(item, newClub))
                .collect(Collectors.toList());
        itemRepository.saveAll(itemList);

        return BoothConverter.toCreateClubDTO(newClub);
    }
}