package com.medha.orderproductservice.product.dto;

import com.medha.orderproductservice.product.domain.Product;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * Wire format returned to callers - including the order-service Feign client,
 * which deserializes this exact shape via {@code ProductClient}.
 */
public record ProductResponse(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        Instant createdAt,
        Instant updatedAt
) {

    public static ProductResponse from(Product product) {
        return new ProductResponse(
                product.getId(),
                product.getSku(),
                product.getName(),
                product.getDescription(),
                product.getPrice(),
                product.getStockQuantity(),
                product.getCreatedAt(),
                product.getUpdatedAt()
        );
    }
}
