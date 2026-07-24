package com.commerceops.payment.web.dto;

import com.commerceops.payment.domain.Payment;

import java.math.BigDecimal;
import java.time.Instant;

public record PaymentResponse(
        Long id,
        String orderId,
        String sagaId,
        BigDecimal amount,
        String currency,
        String status,
        String idempotencyKey,
        String failureReason,
        String provider,
        String providerOrderId,
        String providerPaymentId,
        Instant createdAt
) {
    public static PaymentResponse from(Payment payment) {
        return new PaymentResponse(
                payment.getId(),
                payment.getOrderId(),
                payment.getSagaId(),
                payment.getAmount(),
                payment.getCurrency(),
                payment.getStatus().name(),
                payment.getIdempotencyKey(),
                payment.getFailureReason(),
                payment.getProvider(),
                payment.getProviderOrderId(),
                payment.getProviderPaymentId(),
                payment.getCreatedAt());
    }
}
