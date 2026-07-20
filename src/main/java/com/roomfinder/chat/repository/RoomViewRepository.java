package com.roomfinder.chat.repository;

import com.roomfinder.chat.domain.RoomView;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface RoomViewRepository extends JpaRepository<RoomView, Long> {

    List<RoomView> findByUserIdOrderByViewedAtDesc(Long userId);
}
