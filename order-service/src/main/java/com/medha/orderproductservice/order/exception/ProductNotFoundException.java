package com.medha.orderproductservice.order.exception;

/**
 * Raised when product-service returns 404 for a product referenced in an
 * order - decoded from the HTTP response by {@code ProductClientErrorDecoder}.
 */
public class ProductNotFoundException extends RuntimeException {

    public ProductNotFoundException(String message) {
        super(message);
    }
}
