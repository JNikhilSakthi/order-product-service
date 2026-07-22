package com.medha.orderproductservice.order.client.dto;

/**
 * Outbound request body for the reserve/release stock endpoints on
 * product-service. Mirrors product-service's own {@code StockChangeRequest}
 * by JSON shape only.
 */
public record StockChangeRequest(Integer quantity) {
}
