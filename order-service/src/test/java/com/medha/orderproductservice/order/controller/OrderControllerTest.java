package com.medha.orderproductservice.order.controller;

import com.medha.orderproductservice.order.domain.OrderStatus;
import com.medha.orderproductservice.order.dto.OrderItemRequest;
import com.medha.orderproductservice.order.dto.OrderItemResponse;
import com.medha.orderproductservice.order.dto.OrderRequest;
import com.medha.orderproductservice.order.dto.OrderResponse;
import com.medha.orderproductservice.order.exception.InsufficientStockException;
import com.medha.orderproductservice.order.exception.OrderNotFoundException;
import com.medha.orderproductservice.order.exception.ProductServiceUnavailableException;
import com.medha.orderproductservice.order.service.OrderService;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.webmvc.test.autoconfigure.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;
import tools.jackson.databind.ObjectMapper;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

@WebMvcTest(OrderController.class)
class OrderControllerTest {

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private ObjectMapper objectMapper;

    @MockitoBean
    private OrderService orderService;

    private OrderResponse sampleResponse() {
        return new OrderResponse(1L, "Alice", "alice@example.com", OrderStatus.CONFIRMED,
                new BigDecimal("100.00"),
                List.of(new OrderItemResponse(1L, "Laptop", new BigDecimal("100.00"), 1, new BigDecimal("100.00"))),
                Instant.now(), Instant.now());
    }

    @Test
    void createOrder_returns201_whenValid() throws Exception {
        OrderRequest request = new OrderRequest("Alice", "alice@example.com", List.of(new OrderItemRequest(1L, 1)));
        when(orderService.createOrder(any(OrderRequest.class))).thenReturn(sampleResponse());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isCreated())
                .andExpect(jsonPath("$.status").value("CONFIRMED"))
                .andExpect(jsonPath("$.items[0].productName").value("Laptop"));
    }

    @Test
    void createOrder_returns400_whenPayloadInvalid() throws Exception {
        OrderRequest invalid = new OrderRequest("", "not-an-email", List.of());

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(invalid)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.fieldErrors.customerName").exists())
                .andExpect(jsonPath("$.fieldErrors.customerEmail").exists())
                .andExpect(jsonPath("$.fieldErrors.items").exists());
    }

    @Test
    void createOrder_returns409_whenStockInsufficient() throws Exception {
        OrderRequest request = new OrderRequest("Alice", "alice@example.com", List.of(new OrderItemRequest(1L, 999)));
        when(orderService.createOrder(any(OrderRequest.class)))
                .thenThrow(new InsufficientStockException("Insufficient stock for product 1"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isConflict());
    }

    @Test
    void createOrder_returns503_whenProductServiceDown() throws Exception {
        OrderRequest request = new OrderRequest("Alice", "alice@example.com", List.of(new OrderItemRequest(1L, 1)));
        when(orderService.createOrder(any(OrderRequest.class)))
                .thenThrow(new ProductServiceUnavailableException("product-service unreachable"));

        mockMvc.perform(post("/api/orders")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(objectMapper.writeValueAsString(request)))
                .andExpect(status().isServiceUnavailable());
    }

    @Test
    void getOrderById_returns404_whenMissing() throws Exception {
        when(orderService.getOrderById(404L)).thenThrow(new OrderNotFoundException(404L));

        mockMvc.perform(get("/api/orders/404"))
                .andExpect(status().isNotFound());
    }

    @Test
    void cancelOrder_returns200_withCancelledStatus() throws Exception {
        OrderResponse cancelled = new OrderResponse(1L, "Alice", "alice@example.com", OrderStatus.CANCELLED,
                new BigDecimal("100.00"), List.of(), Instant.now(), Instant.now());
        when(orderService.cancelOrder(eq(1L))).thenReturn(cancelled);

        mockMvc.perform(put("/api/orders/1/cancel"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.status").value("CANCELLED"));
    }
}
