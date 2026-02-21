package com.live_commerce.livebroadcast;

import com.live_commerce.livebroadcast.application.service.CreateBroadcastService;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastAlarmPort;
import com.live_commerce.livebroadcast.domain.port.out.ExternalCompanyPort;
import com.live_commerce.livebroadcast.domain.port.out.LiveBroadcastRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class LiveBroadcastServiceTest {

    @Mock
    private LiveBroadcastRepositoryPort broadcastRepository;

    @Mock
    private ExternalCompanyPort companyPort;

    @Mock
    private BroadcastAlarmPort alarmPort;

    @InjectMocks
    private CreateBroadcastService createBroadcastService;

    @Test
    void contextLoads() {
        // 헥사고날 마이그레이션 완료 - UseCase 기반으로 전환됨
    }
}
