package com.medha.orderproductservice.product.service;

import com.medha.orderproductservice.product.dto.ProductRequest;
import com.medha.orderproductservice.product.dto.ProductResponse;

import java.util.List;

public interface ProductService {

    List<ProductResponse> getAllProducts();

    ProductResponse getProductById(Long id);

    ProductResponse createProduct(ProductRequest request);

    ProductResponse updateProduct(Long id, ProductRequest request);

    void deleteProduct(Long id);

    /**
     * Atomically decrements stock. Called by order-service when an order is placed.
     */
    ProductResponse reserveStock(Long id, int quantity);

    /**
     * Atomically increments stock back. Called by order-service to compensate
     * a partially-reserved order or when an order is cancelled.
     */
    ProductResponse releaseStock(Long id, int quantity);
}
