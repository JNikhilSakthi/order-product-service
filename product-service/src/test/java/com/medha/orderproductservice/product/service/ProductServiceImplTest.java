package com.medha.orderproductservice.product.service;

import com.medha.orderproductservice.product.domain.Product;
import com.medha.orderproductservice.product.dto.ProductRequest;
import com.medha.orderproductservice.product.dto.ProductResponse;
import com.medha.orderproductservice.product.exception.DuplicateSkuException;
import com.medha.orderproductservice.product.exception.InsufficientStockException;
import com.medha.orderproductservice.product.exception.ProductNotFoundException;
import com.medha.orderproductservice.product.repository.ProductRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class ProductServiceImplTest {

    @Mock
    private ProductRepository productRepository;

    @InjectMocks
    private ProductServiceImpl productService;

    private Product product;

    @BeforeEach
    void setUp() {
        product = Product.builder()
                .id(1L)
                .sku("SKU-001")
                .name("Test Widget")
                .description("A widget for testing")
                .price(new BigDecimal("19.99"))
                .stockQuantity(10)
                .version(0L)
                .createdAt(Instant.now())
                .updatedAt(Instant.now())
                .build();
    }

    @Test
    void getProductById_returnsProduct_whenFound() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        ProductResponse response = productService.getProductById(1L);

        assertThat(response.sku()).isEqualTo("SKU-001");
        assertThat(response.stockQuantity()).isEqualTo(10);
    }

    @Test
    void getProductById_throwsNotFound_whenMissing() {
        when(productRepository.findById(99L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> productService.getProductById(99L))
                .isInstanceOf(ProductNotFoundException.class)
                .hasMessageContaining("99");
    }

    @Test
    void createProduct_savesNewProduct_whenSkuIsUnique() {
        ProductRequest request = new ProductRequest("SKU-002", "New Item", "desc", new BigDecimal("5.00"), 3);
        when(productRepository.existsBySku("SKU-002")).thenReturn(false);
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> {
            Product p = invocation.getArgument(0);
            p.setId(2L);
            return p;
        });

        ProductResponse response = productService.createProduct(request);

        assertThat(response.id()).isEqualTo(2L);
        assertThat(response.sku()).isEqualTo("SKU-002");
        verify(productRepository).save(any(Product.class));
    }

    @Test
    void createProduct_throwsDuplicateSku_whenSkuAlreadyExists() {
        ProductRequest request = new ProductRequest("SKU-001", "Dup", "desc", BigDecimal.TEN, 1);
        when(productRepository.existsBySku("SKU-001")).thenReturn(true);

        assertThatThrownBy(() -> productService.createProduct(request))
                .isInstanceOf(DuplicateSkuException.class);

        verify(productRepository, never()).save(any());
    }

    @Test
    void reserveStock_decrementsQuantity_whenEnoughStock() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.reserveStock(1L, 4);

        assertThat(response.stockQuantity()).isEqualTo(6);
        verify(productRepository, times(1)).save(product);
    }

    @Test
    void reserveStock_throwsInsufficientStock_whenNotEnoughStock() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));

        assertThatThrownBy(() -> productService.reserveStock(1L, 999))
                .isInstanceOf(InsufficientStockException.class)
                .hasMessageContaining("Insufficient stock");

        verify(productRepository, never()).save(any());
    }

    @Test
    void releaseStock_incrementsQuantity() {
        when(productRepository.findById(1L)).thenReturn(Optional.of(product));
        when(productRepository.save(any(Product.class))).thenAnswer(invocation -> invocation.getArgument(0));

        ProductResponse response = productService.releaseStock(1L, 5);

        assertThat(response.stockQuantity()).isEqualTo(15);
    }
}
