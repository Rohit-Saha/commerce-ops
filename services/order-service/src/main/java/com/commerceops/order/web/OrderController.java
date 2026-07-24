package com.commerceops.order.web;

import com.commerceops.common.web.ApiMessage;
import com.commerceops.order.service.OrderService;
import com.commerceops.order.web.dto.CancelOrderRequest;
import com.commerceops.order.web.dto.CreateOrderRequest;
import com.commerceops.order.web.dto.OrderResponse;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/orders")
public class OrderController {

    private final OrderService orderService;

    public OrderController(OrderService orderService) {
        this.orderService = orderService;
    }

    @ApiMessage("Order created successfully")
    @PostMapping
    public ResponseEntity<OrderResponse> createOrder(
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestHeader(value = "X-Defer-Fulfillment", required = false) String deferFulfillment,
            @Valid @RequestBody CreateOrderRequest request) {
        boolean startFulfillment = !"true".equalsIgnoreCase(deferFulfillment);
        OrderResponse response = orderService.createOrder(request, idempotencyKey, startFulfillment);
        return ResponseEntity.status(HttpStatus.CREATED).body(response);
    }

    @PostMapping("/{id}/start-fulfillment")
    public OrderResponse startFulfillment(@PathVariable String id) {
        return orderService.startFulfillment(id);
    }

    @PostMapping("/{id}/abandon")
    public OrderResponse abandonPending(
            @PathVariable String id,
            @RequestBody(required = false) CancelOrderRequest request) {
        String reason = request != null ? request.reason() : "payment authorization failed";
        return orderService.abandonPending(id, reason);
    }

    @GetMapping
    public List<OrderResponse> listOrders() {
        return orderService.listOrders();
    }

    @GetMapping("/by-customer/{customerId}")
    public List<OrderResponse> listOrdersByCustomer(@PathVariable String customerId) {
        return orderService.listOrdersByCustomer(customerId);
    }

    @GetMapping("/{id}")
    public OrderResponse getOrder(@PathVariable String id) {
        return orderService.getOrder(id);
    }

    @PostMapping("/{id}/cancel")
    public OrderResponse cancelOrder(@PathVariable String id,
                                      @RequestBody(required = false) CancelOrderRequest request) {
        String reason = request != null ? request.reason() : null;
        return orderService.requestCancel(id, reason);
    }
}
