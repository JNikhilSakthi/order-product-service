package com.medha.orderproductservice.order.service;

import com.medha.orderproductservice.order.client.ProductClient;
import com.medha.orderproductservice.order.client.dto.ProductDto;
import com.medha.orderproductservice.order.client.dto.StockChangeRequest;
import com.medha.orderproductservice.order.domain.Order;
import com.medha.orderproductservice.order.domain.OrderItem;
import com.medha.orderproductservice.order.domain.OrderStatus;
import com.medha.orderproductservice.order.dto.OrderItemRequest;
import com.medha.orderproductservice.order.dto.OrderRequest;
import com.medha.orderproductservice.order.dto.OrderResponse;
import com.medha.orderproductservice.order.exception.InsufficientStockException;
import com.medha.orderproductservice.order.exception.InvalidOrderStateException;
import com.medha.orderproductservice.order.exception.OrderNotFoundException;
import com.medha.orderproductservice.order.exception.ProductNotFoundException;
import com.medha.orderproductservice.order.repository.OrderRepository;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.Optional;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class OrderServiceImplTest {

    @Mock
    private OrderRepository orderRepository;

    @Mock
    private ProductClient productClient;

    @InjectMocks
    private OrderServiceImpl orderService;

    private ProductDto product(long id, String name, String price, int stock) {
        return new ProductDto(id, "SKU-" + id, name, "desc", new BigDecimal(price), stock, Instant.now(), Instant.now());
    }

    @Test
    void createOrder_reservesEachItemAndSavesConfirmedOrder() {
        OrderRequest request = new OrderRequest("Alice", "alice@example.com", List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 1)
        ));

        when(productClient.reserveStock(eq(1L), any(StockChangeRequest.class)))
                .thenReturn(product(1L, "Laptop", "1000.00", 8));
        when(productClient.reserveStock(eq(2L), any(StockChangeRequest.class)))
                .thenReturn(product(2L, "Mouse", "25.00", 20));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));

        OrderResponse response = orderService.createOrder(request);

        assertThat(response.status()).isEqualTo(OrderStatus.CONFIRMED);
        assertThat(response.items()).hasSize(2);
        assertThat(response.totalAmount()).isEqualByComparingTo(new BigDecimal("2025.00"));
        verify(productClient, never()).releaseStock(anyLong(), any());
    }

    @Test
    void createOrder_compensatesAlreadyReservedItems_whenLaterItemHasInsufficientStock() {
        OrderRequest request = new OrderRequest("Bob", "bob@example.com", List.of(
                new OrderItemRequest(1L, 2),
                new OrderItemRequest(2L, 500)
        ));

        when(productClient.reserveStock(eq(1L), any(StockChangeRequest.class)))
                .thenReturn(product(1L, "Laptop", "1000.00", 8));
        when(productClient.reserveStock(eq(2L), any(StockChangeRequest.class)))
                .thenThrow(new InsufficientStockException("Insufficient stock for product 2: requested 500 but only 20 available"));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(InsufficientStockException.class);

        ArgumentCaptor<StockChangeRequest> captor = ArgumentCaptor.forClass(StockChangeRequest.class);
        verify(productClient, times(1)).releaseStock(eq(1L), captor.capture());
        assertThat(captor.getValue().quantity()).isEqualTo(2);
        verify(orderRepository, never()).save(any());
    }

    @Test
    void createOrder_compensates_whenProductDoesNotExist() {
        OrderRequest request = new OrderRequest("Carl", "carl@example.com", List.of(
                new OrderItemRequest(1L, 1),
                new OrderItemRequest(99L, 1)
        ));

        when(productClient.reserveStock(eq(1L), any(StockChangeRequest.class)))
                .thenReturn(product(1L, "Laptop", "1000.00", 8));
        when(productClient.reserveStock(eq(99L), any(StockChangeRequest.class)))
                .thenThrow(new ProductNotFoundException("Product not found with id: 99"));

        assertThatThrownBy(() -> orderService.createOrder(request))
                .isInstanceOf(ProductNotFoundException.class);

        verify(productClient, times(1)).releaseStock(eq(1L), any(StockChangeRequest.class));
    }

    @Test
    void getOrderById_throwsNotFound_whenMissing() {
        when(orderRepository.findById(42L)).thenReturn(Optional.empty());

        assertThatThrownBy(() -> orderService.getOrderById(42L))
                .isInstanceOf(OrderNotFoundException.class);
    }

    @Test
    void cancelOrder_releasesStockForEveryItem_andMarksCancelled() {
        Order order = Order.builder()
                .id(5L)
                .customerName("Dana")
                .customerEmail("dana@example.com")
                .status(OrderStatus.CONFIRMED)
                .totalAmount(new BigDecimal("50.00"))
                .build();
        order.addItem(OrderItem.builder().productId(1L).productName("Laptop").unitPrice(new BigDecimal("1000.00")).quantity(2).build());

        when(orderRepository.findById(5L)).thenReturn(Optional.of(order));
        when(orderRepository.save(any(Order.class))).thenAnswer(invocation -> invocation.getArgument(0));
        when(productClient.releaseStock(eq(1L), any(StockChangeRequest.class)))
                .thenReturn(product(1L, "Laptop", "1000.00", 10));

        OrderResponse response = orderService.cancelOrder(5L);

        assertThat(response.status()).isEqualTo(OrderStatus.CANCELLED);
        verify(productClient).releaseStock(eq(1L), any(StockChangeRequest.class));
    }

    @Test
    void cancelOrder_rejectsAlreadyCancelledOrder() {
        Order order = Order.builder()
                .id(6L)
                .customerName("Eve")
                .customerEmail("eve@example.com")
                .status(OrderStatus.CANCELLED)
                .totalAmount(BigDecimal.ZERO)
                .build();
        when(orderRepository.findById(6L)).thenReturn(Optional.of(order));

        assertThatThrownBy(() -> orderService.cancelOrder(6L))
                .isInstanceOf(InvalidOrderStateException.class);

        verify(productClient, never()).releaseStock(anyLong(), any());
    }
}
