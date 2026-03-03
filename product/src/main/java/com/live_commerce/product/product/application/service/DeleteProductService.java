package com.live_commerce.product.product.application.service;

import com.live_commerce.product.product.domain.exception.ProductException;
import com.live_commerce.product.product.domain.model.Product;
import com.live_commerce.product.product.domain.port.in.DeleteProductUseCase;
import com.live_commerce.product.product.domain.port.out.ExternalCompanyPort;
import com.live_commerce.product.product.domain.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class DeleteProductService implements DeleteProductUseCase {

    private final ProductRepositoryPort productRepositoryPort;
    private final ExternalCompanyPort externalCompanyPort;

    @Transactional
    @Override
    public void deleteProduct(UUID productId, UUID requestUserId, String requestUserRole, UUID requestUserCompanyId) {
        Product product = productRepositoryPort.findById(productId)
                .orElseThrow(ProductException::forProductNotFound);

        boolean isMaster = "ROLE_MASTER".equals(requestUserRole);
        if (!isMaster) {
            UUID companyOwner = externalCompanyPort.getCompanyOwner(product.getCompanyId());
            if (!requestUserId.equals(companyOwner)) {
                throw ProductException.accessDenied();
            }
        }

        product.delete(requestUserId);
        productRepositoryPort.save(product);
    }
}
