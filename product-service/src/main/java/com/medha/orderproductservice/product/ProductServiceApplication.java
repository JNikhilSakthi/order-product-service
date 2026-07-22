package com.medha.orderproductservice.product;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * product-service owns the product catalog and inventory. It is a plain REST
 * provider with no knowledge of order-service - order-service is the OpenFeign
 * consumer that calls the endpoints exposed here. JPA auditing is enabled
 * separately in {@code config.JpaAuditingConfig} - see that class for why.
 */
@SpringBootApplication
public class ProductServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ProductServiceApplication.class, args);
    }
}
