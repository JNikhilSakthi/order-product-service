package com.medha.orderproductservice.product.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

/**
 * Payload for creating or fully updating a product. {@code sku} is the
 * business key that is unique across the catalog.
 */
public record ProductRequest(

        @NotBlank(message = "sku is required")
        String sku,

        @NotBlank(message = "name is required")
        String name,

        String description,

        @NotNull(message = "price is required")
        @DecimalMin(value = "0.01", message = "price must be greater than zero")
        BigDecimal price,

        @NotNull(message = "stockQuantity is required")
        @Min(value = 0, message = "stockQuantity cannot be negative")
        Integer stockQuantity
) {
}
