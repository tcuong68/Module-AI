package com.roomfinder.chat.repository;

import com.roomfinder.chat.domain.Poi;
import org.springframework.data.jpa.repository.JpaRepository;

public interface PoiRepository extends JpaRepository<Poi, Long> {
}
