package com.live_commerce.product.product.application.service;

import com.live_commerce.product.product.application.dto.command.UpdateProductCommand;
import com.live_commerce.product.product.application.dto.result.ProductResult;
import com.live_commerce.product.product.domain.exception.ProductException;
import com.live_commerce.product.product.domain.model.Product;
import com.live_commerce.product.product.domain.port.in.UpdateProductUseCase;
import com.live_commerce.product.product.domain.port.out.ExternalCompanyPort;
import com.live_commerce.product.product.domain.port.out.InventoryQueryPort;
import com.live_commerce.product.product.domain.port.out.ProductRepositoryPort;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.UUID;

@Service
@RequiredArgsConstructor
public class UpdateProductService implements UpdateProductUseCase {

    private final ProductRepositoryPort productRepositoryPort;
    private final ExternalCompanyPort externalCompanyPort;
    private final InventoryQueryPort inventoryQueryPort;

    @Transactional
    @Override
    public ProductResult updateProduct(UpdateProductCommand command) {
        Product product = productRepositoryPort.findById(command.productId())
                .orElseThrow(ProductException::forProductNotFound);

        boolean isMaster = "ROLE_MASTER".equals(command.requestUserRole());
        if (!isMaster) {
            UUID companyOwner = externalCompanyPort.getCompanyOwner(product.getCompanyId());
            if (!command.requestUserId().equals(companyOwner)) {
                throw ProductException.accessDenied();
            }
        }

        product.update(
                command.name(),
                command.description(),
                command.price(),
                command.category(),
                command.productStatus()
        );

        Product saved = productRepositoryPort.save(product);
        boolean soldOut = inventoryQueryPort.isSoldOut(saved.getProductId());
        return ProductResult.from(saved, soldOut);
    }
}
