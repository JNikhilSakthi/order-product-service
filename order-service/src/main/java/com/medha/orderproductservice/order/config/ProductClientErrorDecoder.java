package com.medha.orderproductservice.order.config;

import com.medha.orderproductservice.order.exception.InsufficientStockException;
import com.medha.orderproductservice.order.exception.ProductNotFoundException;
import com.medha.orderproductservice.order.exception.ProductServiceUnavailableException;
import feign.Response;
import feign.Util;
import feign.codec.ErrorDecoder;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import tools.jackson.databind.ObjectMapper;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

/**
 * Translates HTTP-level failures from product-service into typed, meaningful
 * exceptions instead of letting a generic {@link feign.FeignException} leak
 * out of the service layer. This is the piece that makes calling product-service
 * feel like calling a local Java method that can throw a small, well-known set
 * of checked-in-spirit exceptions.
 *
 * <p>Body parsing is defensive: product-service returns a JSON {@code ApiError}
 * with a {@code message} field, but we fall back to the raw body (or the HTTP
 * reason phrase) if parsing fails, so a malformed error response never masks
 * the original failure.
 */
public class ProductClientErrorDecoder implements ErrorDecoder {

    private static final Logger log = LoggerFactory.getLogger(ProductClientErrorDecoder.class);

    private final ErrorDecoder defaultDecoder = new Default();
    private final ObjectMapper objectMapper;

    public ProductClientErrorDecoder(ObjectMapper objectMapper) {
        this.objectMapper = objectMapper;
    }

    @Override
    public Exception decode(String methodKey, Response response) {
        String message = extractMessage(response);
        log.warn("product-service call [{}] failed with status {}: {}", methodKey, response.status(), message);

        return switch (response.status()) {
            case 404 -> new ProductNotFoundException(message);
            case 409 -> new InsufficientStockException(message);
            default -> {
                if (response.status() >= 500) {
                    yield new ProductServiceUnavailableException(
                            "product-service returned status " + response.status() + ": " + message);
                }
                yield defaultDecoder.decode(methodKey, response);
            }
        };
    }

    private String extractMessage(Response response) {
        if (response.body() == null) {
            return response.reason() != null ? response.reason() : "no error body";
        }
        try {
            String body = Util.toString(response.body().asReader(StandardCharsets.UTF_8));
            RemoteApiError remoteError = objectMapper.readValue(body, RemoteApiError.class);
            return remoteError.message() != null ? remoteError.message() : body;
        } catch (IOException | RuntimeException e) {
            return "Unable to parse product-service error response";
        }
    }

    /**
     * Minimal local shadow of product-service's {@code ApiError} - only the
     * field we actually need to surface, so this decoder does not break if
     * the remote error shape gains new fields.
     */
    private record RemoteApiError(String message) {
    }
}
