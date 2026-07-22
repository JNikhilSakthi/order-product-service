package com.medha.orderproductservice.order.exception;

import java.time.Instant;
import java.util.Map;

/**
 * Uniform error body returned by every order-service endpoint. Deliberately
 * the same JSON shape as product-service's {@code ApiError} so API consumers
 * see one consistent error contract across the whole system, even though the
 * two classes are not shared code.
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
