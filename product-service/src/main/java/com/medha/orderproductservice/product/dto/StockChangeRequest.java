package com.medha.orderproductservice.product.dto;

import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;

/**
 * Body for the reserve/release stock endpoints called by order-service's
 * Feign client whenever an order is placed or cancelled.
 */
public record StockChangeRequest(

        @NotNull(message = "quantity is required")
        @Min(value = 1, message = "quantity must be at least 1")
        Integer quantity
) {
}
