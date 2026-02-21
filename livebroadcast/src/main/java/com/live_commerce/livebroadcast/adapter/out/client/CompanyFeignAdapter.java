package com.live_commerce.livebroadcast.adapter.out.client;

import com.live_commerce.livebroadcast.domain.port.out.ExternalCompanyPort;
import com.live_commerce.livebroadcast.infrastructure.client.company.CompanyClient;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CompanyFeignAdapter implements ExternalCompanyPort {

    private final CompanyClient companyClient;

    @Override
    public boolean existsCompany(UUID companyId) {
        try {
            return companyClient.getCompany(companyId).getData() != null;
        } catch (FeignException.NotFound e) {
            return false;
        }
    }
}
