package com.commerceops.order.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record OrderResponse(
        String id,
        String customerId,
        String status,
        BigDecimal totalAmount,
        String currency,
        String idempotencyKey,
        List<OrderLineResponse> lines,
        ShippingAddressDto shippingAddress,
        Instant createdAt,
        Instant updatedAt
) {
}
