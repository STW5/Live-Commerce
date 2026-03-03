package com.live_commerce.product.product.domain.port.out;

import java.util.UUID;

public interface ExternalCompanyPort {
    boolean existsActiveCompany(UUID companyId);
    UUID getCompanyOwner(UUID companyId);
}
