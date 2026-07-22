package com.medha.orderproductservice.order.service;

import com.medha.orderproductservice.order.dto.OrderRequest;
import com.medha.orderproductservice.order.dto.OrderResponse;

import java.util.List;

public interface OrderService {

    List<OrderResponse> getAllOrders();

    OrderResponse getOrderById(Long id);

    OrderResponse createOrder(OrderRequest request);

    OrderResponse cancelOrder(Long id);
}
