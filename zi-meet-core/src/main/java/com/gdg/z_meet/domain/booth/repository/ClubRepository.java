package com.gdg.z_meet.domain.booth.repository;

import com.gdg.z_meet.domain.booth.entity.Club;
import com.gdg.z_meet.domain.booth.entity.Place;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface ClubRepository extends JpaRepository<Club, Long> {

    Optional<Club> findByName(String name);
    List<Club> findByPlace(Place place);
}
