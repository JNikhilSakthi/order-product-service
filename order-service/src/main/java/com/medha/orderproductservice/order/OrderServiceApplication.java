package com.medha.orderproductservice.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * order-service places orders against product-service's catalog. OpenFeign is
 * activated via {@code @EnableFeignClients} in {@code config.FeignClientsEnablement}
 * and JPA auditing via {@code config.JpaAuditingConfig} - both live in their own
 * configuration classes, not on this one, so that {@code @WebMvcTest} slice
 * tests (which use this class only as a marker, not a fully initialized app)
 * do not try to build Feign/JPA infrastructure. See those classes for details.
 */
@SpringBootApplication
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
