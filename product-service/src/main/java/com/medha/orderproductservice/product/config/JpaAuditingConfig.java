package com.medha.orderproductservice.product.config;

import org.springframework.context.annotation.Configuration;
import org.springframework.data.jpa.repository.config.EnableJpaAuditing;

/**
 * Kept as its own {@code @Configuration} class (rather than annotating
 * {@code ProductServiceApplication} directly) so that {@code @WebMvcTest}
 * web-layer slice tests - which do not load JPA auto-configuration - are not
 * dragged into trying to build the JPA auditing infrastructure.
 */
@Configuration
@EnableJpaAuditing
public class JpaAuditingConfig {
}
