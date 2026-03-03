package com.live_commerce.product.product.adapter.out.external;

import com.live_commerce.product.product.domain.exception.ProductException;
import com.live_commerce.product.product.domain.port.out.ExternalCompanyPort;
import com.live_commerce.product.product.infrastructure.client.CompanyClient;
import com.live_commerce.product.product.infrastructure.client.ExternalCompanyResponseDto;
import com.live_commerce.product.product.presentation.common.ApiResponse;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Component;

import java.util.UUID;

@Component
@RequiredArgsConstructor
public class CompanyFeignAdapter implements ExternalCompanyPort {

    private final CompanyClient companyClient;

    @Override
    public boolean existsActiveCompany(UUID companyId) {
        try {
            ApiResponse<ExternalCompanyResponseDto> response = companyClient.getCompany(companyId);
            return response.getData() != null;
        } catch (FeignException.NotFound e) {
            return false;
        } catch (FeignException e) {
            throw new RuntimeException("업체 서비스 호출 실패", e);
        }
    }

    @Override
    public UUID getCompanyOwner(UUID companyId) {
        try {
            ApiResponse<ExternalCompanyResponseDto> response = companyClient.getCompany(companyId);
            ExternalCompanyResponseDto company = response.getData();
            if (company == null) {
                throw ProductException.forExternalCompanyNotFound();
            }
            return company.owner();
        } catch (FeignException.NotFound e) {
            throw ProductException.forExternalCompanyNotFound();
        } catch (FeignException e) {
            throw new RuntimeException("업체 서비스 호출 실패", e);
        }
    }
}
