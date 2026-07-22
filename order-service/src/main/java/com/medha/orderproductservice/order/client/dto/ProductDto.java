package com.medha.orderproductservice.order.client.dto;

import com.fasterxml.jackson.annotation.JsonIgnoreProperties;

import java.math.BigDecimal;
import java.time.Instant;

/**
 * order-service's local view of a product, deserialized from
 * product-service's {@code ProductResponse} JSON. The two services do not
 * share a Java model - only the JSON contract - which is why this class lives
 * under {@code client.dto} rather than reusing product-service's DTO.
 */
@JsonIgnoreProperties(ignoreUnknown = true)
public record ProductDto(
        Long id,
        String sku,
        String name,
        String description,
        BigDecimal price,
        Integer stockQuantity,
        Instant createdAt,
        Instant updatedAt
) {
}
