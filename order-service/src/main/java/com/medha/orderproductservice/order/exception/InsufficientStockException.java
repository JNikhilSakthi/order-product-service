package com.medha.orderproductservice.order.exception;

/**
 * Raised when product-service returns 409 because it does not have enough
 * stock to satisfy a reservation - decoded by {@code ProductClientErrorDecoder}.
 */
public class InsufficientStockException extends RuntimeException {

    public InsufficientStockException(String message) {
        super(message);
    }
}
