package com.roomfinder.chat.domain;

import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;

/**
 * Ánh xạ bảng `room` (đã bổ sung cột theo §8).
 * distance_m KHÔNG lưu trong DB — tính runtime khi tìm theo POI.
 */
@Entity
@Table(name = "room")
@Getter
@Setter
public class Room {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String title;
    private Long price;                 // VND
    private BigDecimal area;            // m2
    private String district;
    private String addressText;

    private BigDecimal latitude;
    private BigDecimal longitude;
    private String status;

    private Boolean hasAirConditioner;
    private Boolean hasParking;
    private Boolean hasWifi;
    private Boolean hasWashingMachine;
    private Boolean isPrivateBathroom;

    private String roomType;
    @Column(columnDefinition = "TEXT")
    private String description;
    private String thumbnail;

    /** Khoảng cách (m) tới POI — chỉ set khi tìm theo "gần X". Không map cột. */
    @Transient
    private Double distanceM;
}
