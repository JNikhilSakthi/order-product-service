package com.medha.orderproductservice.order.exception;

/**
 * Raised when product-service is unreachable, times out, or returns a 5xx -
 * i.e. failures that are not the caller's fault. Surfaced to API clients of
 * order-service as 503 Service Unavailable.
 */
public class ProductServiceUnavailableException extends RuntimeException {

    public ProductServiceUnavailableException(String message) {
        super(message);
    }

    public ProductServiceUnavailableException(String message, Throwable cause) {
        super(message, cause);
    }
}
