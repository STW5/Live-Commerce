package com.live_commerce.livebroadcast.application.dto.command;

import com.live_commerce.livebroadcast.domain.model.BroadcastStatus;

import java.time.LocalDateTime;

public record UpdateBroadcastCommand(
        String broadcastName,
        LocalDateTime startTime,
        LocalDateTime endTime,
        BroadcastStatus broadcastStatus
) {}
