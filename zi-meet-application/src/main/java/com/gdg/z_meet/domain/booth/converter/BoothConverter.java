package com.gdg.z_meet.domain.booth.converter;

import com.gdg.z_meet.domain.booth.dto.BoothRequestDTO;
import com.gdg.z_meet.domain.booth.dto.BoothResponseDTO;
import com.gdg.z_meet.domain.booth.entity.Category;
import com.gdg.z_meet.domain.booth.entity.Club;
import com.gdg.z_meet.domain.booth.entity.Item;
import com.gdg.z_meet.domain.booth.entity.Place;

import java.util.List;
import java.util.stream.Collectors;

public class BoothConverter {

    public static Club toClub(BoothRequestDTO.CreateClubDTO request){

        return Club.builder()
                .place(Place.valueOf(request.getPlace()))
                .name(request.getName())
                .rep(request.getRep())
                .category(Category.valueOf(request.getCategory()))
                .account(request.getAccount())
                .time(request.getTime())
                .info(request.getInfo())
                .build();
    }

    public static Item toItem(BoothRequestDTO.CreateItemDTO item, Club club){

        return Item.builder()
                .name(item.getName())
                .content(item.getContent())
                .club(club)
                .build();
    }

    public static BoothResponseDTO.CreateClubDTO toCreateClubDTO(Club club){

        return BoothResponseDTO.CreateClubDTO.builder()
                .clubId(club.getId())
                .build();
    }

    public static BoothResponseDTO.GetAllClubDTO toGetAllClubDTO(List<Club> clubList){

        List<BoothResponseDTO.preClubDTO> clubDTOS = clubList.stream()
                .map(club -> BoothResponseDTO.preClubDTO.builder()
                        .clubId(club.getId())
                        .name(club.getName())
                        .rep(club.getRep())
                        .category(club.getCategory().toString())
                        .account(club.getAccount())
                        .build())
                .collect(Collectors.toList());

        return BoothResponseDTO.GetAllClubDTO.builder()
                .clubList(clubDTOS)
                .build();
    }

    public static BoothResponseDTO.GetClubDTO toGetClubDTO(List<Item> itemList, Club club){

        List<BoothResponseDTO.itemDTO> itemDTOS = itemList.stream()
                .map(item -> BoothResponseDTO.itemDTO.builder()
                        .itemId(item.getId())
                        .name(item.getName())
                        .content(item.getContent())
                        .build())
                .collect(Collectors.toList());

        return BoothResponseDTO.GetClubDTO.builder()
                .clubId(club.getId())
                .name(club.getName())
                .category(club.getCategory().toString())
                .itemList(itemDTOS)
                .account(club.getAccount())
                .time(club.getTime())
                .info(club.getInfo())
                .build();
    }
}
