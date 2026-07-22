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
import com.medha.orderproductservice.order.exception.ProductServiceUnavailableException;
import com.medha.orderproductservice.order.repository.OrderRepository;
import feign.FeignException;
import lombok.RequiredArgsConstructor;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;

@Service
@RequiredArgsConstructor
public class OrderServiceImpl implements OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderServiceImpl.class);

    private final OrderRepository orderRepository;
    private final ProductClient productClient;

    @Override
    @Transactional(readOnly = true)
    public List<OrderResponse> getAllOrders() {
        return orderRepository.findAll().stream()
                .map(OrderResponse::from)
                .toList();
    }

    @Override
    @Transactional(readOnly = true)
    public OrderResponse getOrderById(Long id) {
        return OrderResponse.from(findOrderOrThrow(id));
    }

    /**
     * Places an order by reserving stock for every line item, one Feign call
     * per item, in request order. If any reservation fails partway through,
     * the items already reserved are compensated with best-effort release
     * calls before the original failure is rethrown - there is no distributed
     * transaction here, only sequential calls plus manual compensation, which
     * is exactly the trade-off a synchronous REST-over-Feign integration
     * forces you to reason about explicitly.
     */
    @Override
    @Transactional
    public OrderResponse createOrder(OrderRequest request) {
        List<ReservedLine> reserved = new ArrayList<>();

        for (OrderItemRequest itemRequest : request.items()) {
            try {
                ProductDto product = productClient.reserveStock(
                        itemRequest.productId(),
                        new StockChangeRequest(itemRequest.quantity()));
                reserved.add(new ReservedLine(product, itemRequest.quantity()));
            } catch (ProductNotFoundException | InsufficientStockException e) {
                compensate(reserved);
                throw e;
            } catch (FeignException e) {
                compensate(reserved);
                throw new ProductServiceUnavailableException(
                        "product-service call failed while placing the order: " + e.getMessage(), e);
            }
        }

        Order order = Order.builder()
                .customerName(request.customerName())
                .customerEmail(request.customerEmail())
                .status(OrderStatus.CONFIRMED)
                .totalAmount(BigDecimal.ZERO)
                .build();

        BigDecimal total = BigDecimal.ZERO;
        for (ReservedLine line : reserved) {
            OrderItem item = OrderItem.builder()
                    .productId(line.product().id())
                    .productName(line.product().name())
                    .unitPrice(line.product().price())
                    .quantity(line.quantity())
                    .build();
            order.addItem(item);
            total = total.add(item.getSubtotal());
        }
        order.setTotalAmount(total);

        return OrderResponse.from(orderRepository.save(order));
    }

    /**
     * Cancels a confirmed order and releases the stock it was holding back to
     * product-service. Already-cancelled orders are rejected rather than
     * silently accepted, to keep stock release strictly one-to-one with a
     * successful reservation.
     */
    @Override
    @Transactional
    public OrderResponse cancelOrder(Long id) {
        Order order = findOrderOrThrow(id);
        if (order.getStatus() == OrderStatus.CANCELLED) {
            throw new InvalidOrderStateException("Order " + id + " is already cancelled");
        }

        for (OrderItem item : order.getItems()) {
            try {
                productClient.releaseStock(item.getProductId(), new StockChangeRequest(item.getQuantity()));
            } catch (FeignException e) {
                log.warn("Failed to release stock for product {} while cancelling order {}: {}",
                        item.getProductId(), id, e.getMessage());
            }
        }

        order.setStatus(OrderStatus.CANCELLED);
        return OrderResponse.from(orderRepository.save(order));
    }

    private void compensate(List<ReservedLine> reserved) {
        for (ReservedLine line : reserved) {
            try {
                productClient.releaseStock(line.product().id(), new StockChangeRequest(line.quantity()));
            } catch (FeignException compensationFailure) {
                log.warn("Compensation failed for product {}: stock may be under-released. Manual reconciliation needed.",
                        line.product().id(), compensationFailure);
            }
        }
    }

    private Order findOrderOrThrow(Long id) {
        return orderRepository.findById(id)
                .orElseThrow(() -> new OrderNotFoundException(id));
    }

    private record ReservedLine(ProductDto product, int quantity) {
    }
}
