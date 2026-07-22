package com.medha.orderproductservice.product.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error body returned by every product-service endpoint. This exact
 * JSON shape is what order-service's Feign {@code ErrorDecoder} parses when
 * translating a failed HTTP call back into a typed exception - the two
 * services are independently deployable, so the contract is the JSON shape,
 * not a shared Java class.
 */
public record ApiError(
        Instant timestamp,
        int status,
        String error,
        String message,
        String path,
        Map<String, String> fieldErrors
) {

    public static ApiError of(int status, String error, String message, String path) {
        return new ApiError(Instant.now(), status, error, message, path, null);
    }

    public static ApiError of(int status, String error, String message, String path, Map<String, String> fieldErrors) {
        return new ApiError(Instant.now(), status, error, message, path, fieldErrors);
    }
}
