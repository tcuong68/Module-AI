package com.roomfinder.chat.dto;

import com.roomfinder.chat.model.Filters;
import lombok.Builder;
import lombok.Getter;

import java.util.List;

/** Response của POST /api/v1/chat — §7.1. */
@Getter
@Builder
public class ChatResponse {
    private String sessionId;
    private String reply;
    private String intent;
    private List<RoomCardDto> rooms;
    private Filters activeFilters;
    private MetaDto meta;
}
