package com.medha.orderproductservice.product.repository;

import com.medha.orderproductservice.product.config.JpaAuditingConfig;
import com.medha.orderproductservice.product.domain.Product;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.data.jpa.test.autoconfigure.DataJpaTest;
import org.springframework.context.annotation.Import;

import java.math.BigDecimal;

import static org.assertj.core.api.Assertions.assertThat;

@DataJpaTest
@Import(JpaAuditingConfig.class)
class ProductRepositoryTest {

    @Autowired
    private ProductRepository productRepository;

    @Test
    void findBySku_returnsProduct_whenExists() {
        Product product = Product.builder()
                .sku("SKU-TEST")
                .name("Integration Test Product")
                .description("desc")
                .price(new BigDecimal("15.00"))
                .stockQuantity(7)
                .build();
        productRepository.save(product);

        assertThat(productRepository.findBySku("SKU-TEST")).isPresent();
        assertThat(productRepository.existsBySku("SKU-TEST")).isTrue();
        assertThat(productRepository.existsBySku("SKU-NOPE")).isFalse();
    }

    @Test
    void save_populatesAuditingAndVersionFields() {
        Product product = Product.builder()
                .sku("SKU-AUDIT")
                .name("Audited Product")
                .price(new BigDecimal("1.00"))
                .stockQuantity(1)
                .build();

        Product saved = productRepository.saveAndFlush(product);

        assertThat(saved.getId()).isNotNull();
        assertThat(saved.getCreatedAt()).isNotNull();
        assertThat(saved.getUpdatedAt()).isNotNull();
        assertThat(saved.getVersion()).isZero();
    }
}
