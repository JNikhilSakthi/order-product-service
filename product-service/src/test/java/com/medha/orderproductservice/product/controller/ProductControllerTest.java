package com.medha.orderproductservice.product.controller;

import com.medha.orderproductservice.product.dto.ProductRequest;
import com.medha.orderproductservice.product.dto.ProductResponse;
import com.medha.orderproductservice.product.dto.StockChangeRequest;
import com.medha.orderproductservice.product.exception.InsufficientStockException;
import com.medha.orderproductservice.product.exception.ProductNotFoundException;
import com.medha.orderproductservice.product.service.ProductService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.content;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(ProductController.class)
class ProductControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private ProductService productService;

    private ProductResponse sampleResponse() {
        return new ProductResponse(1L, "SKU-001", "Widget", "desc", new BigDecimal("19.99"), 10,
                Instant.now(), Instant.now());
    }

    @Test
    void getProductById_returns200_withBody() throws Exception {
        when(productService.getProductById(1L)).thenReturn(sampleResponse());

        mockMvc.perform(get("/api/products/1"))
                .andExpect(status().isOk())
                .andExpect(content().contentType(MediaType.APPLICATION_JSON))
                .andExpect(jsonPath("$.sku").value("SKU-001"))
                .andExpect(jsonPath("$.stockQuantity").value(10));
    }

    @Test
    void getProductById_returns404_whenNotFound() throws Exception {
        when(productService.getProductById(99L)).thenThrow(new ProductNotFoundException(99L));

        mockMvc.perform(get("/api/products/99"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.status").value(404))
                .andExpect(jsonPath("$.message").value("Product not found with id: 99"));
    }

    @Test
    void createProduct_returns201_whenValid() throws Exception {
        ProductRequest request = new ProductRequest("SKU-002", "New", "desc", new BigDecimal("9.99"), 5);
        when(productService.createProduct(any(ProductRequest.class))).thenReturn(
                new ProductResponse(2L, "SKU-002", "New", "desc", new BigDecimal("9.99"), 5, Instant.now(), Instant.now()));

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.id").value(2))
                .andExpect(jsonPath("$.sku").value("SKU-002"));
    }

    @Test
    void createProduct_returns400_whenInvalidPayload() throws Exception {
        ProductRequest invalid = new ProductRequest("", "New", "desc", new BigDecimal("-1"), -5);

        mockMvc.perform(post("/api/products")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.sku").exists())
                .andExpect(jsonPath("$.fieldErrors.price").exists());
    }

    @Test
    void reserveStock_returns409_whenInsufficientStock() throws Exception {
        StockChangeRequest request = new StockChangeRequest(1000);
        when(productService.reserveStock(eq(1L), anyInt()))
                .thenThrow(new InsufficientStockException(1L, 1000, 10));

        mockMvc.perform(post("/api/products/1/reserve")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.status").value(409));
    }
}
