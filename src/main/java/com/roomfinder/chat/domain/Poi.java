package com.roomfinder.chat.domain;

import com.roomfinder.chat.util.StringListJsonConverter;
import jakarta.persistence.*;
import lombok.Getter;
import lombok.Setter;

import java.math.BigDecimal;
import java.util.List;

/** Điểm mốc (Point of Interest) — bảng `poi`, §5.2. */
@Entity
@Table(name = "poi")
@Getter
@Setter
public class Poi {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    private String name;

    /** Cột JSON: ["PTIT","Học viện Bưu chính",...]. */
    @Convert(converter = StringListJsonConverter.class)
    @Column(columnDefinition = "json")
    private List<String> aliases;

    private String type;
    private BigDecimal latitude;
    private BigDecimal longitude;

    /** "manual" (nhập tay, seed) | "geocoded" (tự động qua Nominatim — TODO.md Tầng 2). */
    private String source = "manual";
}
