package com.live_commerce.livebroadcast;

import com.live_commerce.livebroadcast.application.service.ConnectBroadcastProductService;
import com.live_commerce.livebroadcast.domain.port.out.BroadcastProductRepositoryPort;
import com.live_commerce.livebroadcast.domain.port.out.ExternalProductPort;
import com.live_commerce.livebroadcast.domain.port.out.LiveBroadcastRepositoryPort;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class BroadcastProductServiceTest {

    @Mock
    private LiveBroadcastRepositoryPort broadcastRepository;

    @Mock
    private BroadcastProductRepositoryPort productRepository;

    @Mock
    private ExternalProductPort productPort;

    @InjectMocks
    private ConnectBroadcastProductService connectBroadcastProductService;

    @Test
    void contextLoads() {
        // 헥사고날 마이그레이션 완료 - UseCase 기반으로 전환됨
    }
}
