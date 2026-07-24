package com.commerceops.saga.web;

import com.commerceops.saga.domain.SagaInstance;

import java.time.Instant;

public record SagaResponse(
        Long id,
        String orderId,
        String status,
        String currentStep,
        String reservationId,
        String paymentId,
        String shipmentId,
        String customerId,
        int retryCount,
        Instant stepDeadline,
        String lastError,
        Instant createdAt,
        Instant updatedAt
) {
    public static SagaResponse from(SagaInstance saga) {
        return new SagaResponse(
                saga.getId(),
                saga.getOrderId(),
                saga.getStatus().name(),
                saga.getCurrentStep(),
                saga.getReservationId(),
                saga.getPaymentId(),
                saga.getShipmentId(),
                saga.getCustomerId(),
                saga.getRetryCount(),
                saga.getStepDeadline(),
                saga.getLastError(),
                saga.getCreatedAt(),
                saga.getUpdatedAt()
        );
    }
}
