package com.live_commerce.product.product.application.service;

import com.live_commerce.product.product.application.dto.command.CreateProductCommand;
import com.live_commerce.product.product.application.dto.result.ProductResult;
import com.live_commerce.product.product.domain.exception.ProductException;
import com.live_commerce.product.product.domain.model.Product;
import com.live_commerce.product.product.domain.port.in.CreateProductUseCase;
import com.live_commerce.product.product.domain.port.out.ExternalCompanyPort;
import com.live_commerce.product.product.domain.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class CreateProductService implements CreateProductUseCase {

    private final ProductRepositoryPort productRepositoryPort;
    private final ExternalCompanyPort externalCompanyPort;

    @Transactional
    @Override
    public ProductResult createProduct(CreateProductCommand command) {
        if (!externalCompanyPort.existsActiveCompany(command.companyId())) {
            throw ProductException.forExternalCompanyNotFound();
        }

        boolean isMaster = "ROLE_MASTER".equals(command.requestUserRole());
        if (!isMaster) {
            UUID companyOwner = externalCompanyPort.getCompanyOwner(command.companyId());
            if (!command.requestUserId().equals(companyOwner)) {
                throw ProductException.accessDenied();
            }
        }

        Product product = Product.create(
                command.companyId(),
                command.name(),
                command.description(),
                command.price(),
                command.category()
        );

        Product saved = productRepositoryPort.save(product);
        return ProductResult.from(saved, false);
    }
}
