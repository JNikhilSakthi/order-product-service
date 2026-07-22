package com.medha.orderproductservice.product.controller;

import com.medha.orderproductservice.product.dto.ProductRequest;
import com.medha.orderproductservice.product.dto.ProductResponse;
import com.medha.orderproductservice.product.dto.StockChangeRequest;
import com.medha.orderproductservice.product.service.ProductService;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

/**
 * REST surface consumed by both human/API clients and, most importantly,
 * order-service's {@code ProductClient} Feign interface. The path/method
 * signatures here must stay in lockstep with that interface.
 */
@RestController
@RequestMapping("/api/products")
@RequiredArgsConstructor
public class ProductController {

    private final ProductService productService;

    @GetMapping
    public List<ProductResponse> getAllProducts() {
        return productService.getAllProducts();
    }

    @GetMapping("/{id}")
    public ProductResponse getProductById(@PathVariable Long id) {
        return productService.getProductById(id);
    }

    @PostMapping
    public ResponseEntity<ProductResponse> createProduct(@Valid @RequestBody ProductRequest request) {
        ProductResponse created = productService.createProduct(request);
        return ResponseEntity.status(HttpStatus.CREATED).body(created);
    }

    @PutMapping("/{id}")
    public ProductResponse updateProduct(@PathVariable Long id, @Valid @RequestBody ProductRequest request) {
        return productService.updateProduct(id, request);
    }

    @DeleteMapping("/{id}")
    public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
        productService.deleteProduct(id);
        return ResponseEntity.noContent().build();
    }

    @PostMapping("/{id}/reserve")
    public ProductResponse reserveStock(@PathVariable Long id, @Valid @RequestBody StockChangeRequest request) {
        return productService.reserveStock(id, request.quantity());
    }

    @PostMapping("/{id}/release")
    public ProductResponse releaseStock(@PathVariable Long id, @Valid @RequestBody StockChangeRequest request) {
        return productService.releaseStock(id, request.quantity());
    }
}
