package com.live_commerce.livebroadcast.domain.port.out;

import java.util.UUID;

public interface ExternalCompanyPort {
    boolean existsCompany(UUID companyId);
}
