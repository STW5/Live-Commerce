package com.live_commerce.livebroadcast.application.dto.command;

import java.time.LocalDateTime;

public record CreateBroadcastCommand(
        String broadcastName,
        LocalDateTime startTime,
        LocalDateTime endTime
) {}
