package com.live_commerce.livebroadcast.domain.port.in;

import com.live_commerce.livebroadcast.application.dto.command.CreateBroadcastCommand;
import com.live_commerce.livebroadcast.application.dto.result.LiveBroadcastResult;

import java.util.UUID;

public interface CreateBroadcastUseCase {
    LiveBroadcastResult createBroadcast(CreateBroadcastCommand command, UUID hostId, UUID companyId);
}
