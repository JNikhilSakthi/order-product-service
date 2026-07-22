package com.medha.orderproductservice.order.config;

import org.springframework.cloud.openfeign.EnableFeignClients;
import org.springframework.context.annotation.Configuration;

/**
 * Kept as its own {@code @Configuration} class (rather than annotating
 * {@code OrderServiceApplication} directly) for the same reason as
 * {@link JpaAuditingConfig}: annotations placed on the class Spring Boot Test
 * uses as the root {@code @SpringBootConfiguration} are processed even by
 * slice tests such as {@code @WebMvcTest}. Since those slices do not fully
 * initialize property sources the way a real application run does,
 * {@code @FeignClient}'s {@code url = "${product-service.url}"} placeholder
 * would otherwise fail to resolve while loading an {@code OrderController}-only
 * web slice.
 */
@Configuration
@EnableFeignClients(basePackages = "com.medha.orderproductservice.order.client")
public class FeignClientsEnablement {
}
