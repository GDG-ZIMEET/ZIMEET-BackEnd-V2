package com.gdg.z_meet.domain.booth.repository;

import com.gdg.z_meet.domain.booth.entity.Item;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ItemRepository extends JpaRepository<Item, Long> {

    List<Item> findByClubId(Long clubId);
}
