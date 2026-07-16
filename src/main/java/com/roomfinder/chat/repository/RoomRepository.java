package com.roomfinder.chat.repository;

import com.roomfinder.chat.domain.Room;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;

/**
 * Truy vấn phòng — NGUYÊN TẮC VÀNG (§5.1): filter cứng bằng SQL.
 * Mọi ràng buộc số học (giá, diện tích, bán kính) đều là filter SQL,
 * không giao cho embedding.
 */
public interface RoomRepository extends JpaRepository<Room, Long> {

    /** Tìm theo bộ lọc thường (không có POI) — sắp xếp theo giá tăng dần. */
    @Query(value = """
        SELECT * FROM room r
        WHERE r.status = 'AVAILABLE'
          AND (:priceMin  IS NULL OR r.price >= :priceMin)
          AND (:priceMax  IS NULL OR r.price <= :priceMax)
          AND (:district  IS NULL OR r.district LIKE CONCAT('%', :district, '%'))
          AND (:areaMin   IS NULL OR r.area >= :areaMin)
          AND (:hasAc     IS NULL OR r.has_air_conditioner = :hasAc)
          AND (:hasParking IS NULL OR r.has_parking = :hasParking)
          AND (:hasWifi   IS NULL OR r.has_wifi = :hasWifi)
          AND (:hasWashing IS NULL OR r.has_washing_machine = :hasWashing)
          AND (:roomType  IS NULL OR r.room_type = :roomType)
        ORDER BY r.price ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Room> searchByFilters(
            @Param("priceMin") Long priceMin,
            @Param("priceMax") Long priceMax,
            @Param("district") String district,
            @Param("areaMin") Double areaMin,
            @Param("hasAc") Boolean hasAc,
            @Param("hasParking") Boolean hasParking,
            @Param("hasWifi") Boolean hasWifi,
            @Param("hasWashing") Boolean hasWashing,
            @Param("roomType") String roomType,
            @Param("limit") int limit);

    /**
     * Tìm theo bán kính quanh POI — §5.2 (ST_Distance_Sphere).
     * distance_m KHÔNG map vào entity (Room.distanceM là @Transient),
     * service sẽ tự tính lại bằng Haversine để hiển thị.
     */
    @Query(value = """
        SELECT * FROM room r
        WHERE r.status = 'AVAILABLE'
          AND r.latitude IS NOT NULL AND r.longitude IS NOT NULL
          AND (:priceMin  IS NULL OR r.price >= :priceMin)
          AND (:priceMax  IS NULL OR r.price <= :priceMax)
          AND (:areaMin   IS NULL OR r.area >= :areaMin)
          AND (:hasAc     IS NULL OR r.has_air_conditioner = :hasAc)
          AND (:hasParking IS NULL OR r.has_parking = :hasParking)
          AND (:hasWifi   IS NULL OR r.has_wifi = :hasWifi)
          AND (:hasWashing IS NULL OR r.has_washing_machine = :hasWashing)
          AND (:roomType  IS NULL OR r.room_type = :roomType)
          AND ST_Distance_Sphere(POINT(r.longitude, r.latitude),
                                 POINT(:lng, :lat)) <= :radiusM
        ORDER BY ST_Distance_Sphere(POINT(r.longitude, r.latitude),
                                    POINT(:lng, :lat)) ASC
        LIMIT :limit
        """, nativeQuery = true)
    List<Room> searchByGeo(
            @Param("priceMin") Long priceMin,
            @Param("priceMax") Long priceMax,
            @Param("areaMin") Double areaMin,
            @Param("hasAc") Boolean hasAc,
            @Param("hasParking") Boolean hasParking,
            @Param("hasWifi") Boolean hasWifi,
            @Param("hasWashing") Boolean hasWashing,
            @Param("roomType") String roomType,
            @Param("lat") double lat,
            @Param("lng") double lng,
            @Param("radiusM") double radiusM,
            @Param("limit") int limit);
}
