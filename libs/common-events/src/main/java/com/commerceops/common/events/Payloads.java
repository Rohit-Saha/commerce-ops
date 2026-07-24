package com.commerceops.common.events;

import java.math.BigDecimal;
import java.util.List;

public final class Payloads {
    private Payloads() {}

    public record OrderLine(String sku, int quantity, BigDecimal unitPrice) {}

    public record OrderCreated(
            String orderId,
            String customerId,
            List<OrderLine> lines,
            BigDecimal totalAmount,
            String currency,
            ShippingAddress shippingAddress
    ) {
        /** Backward-compatible constructor used by older producers. */
        public OrderCreated(
                String orderId,
                String customerId,
                List<OrderLine> lines,
                BigDecimal totalAmount,
                String currency) {
            this(orderId, customerId, lines, totalAmount, currency, null);
        }
    }

    public record OrderStatusChanged(String orderId, String status, String reason) {}

    public record OrderCancelRequested(String orderId, String reason) {}

    public record ReserveInventory(String orderId, String sagaId, List<OrderLine> lines) {}

    public record ReleaseInventory(String orderId, String sagaId, String reservationId) {}

    public record InventoryReserved(String orderId, String sagaId, String reservationId) {}

    public record InventoryReserveFailed(String orderId, String sagaId, String reason) {}

    public record InventoryReleased(String orderId, String sagaId, String reservationId) {}

    public record StockItemChanged(
            String sku,
            String name,
            BigDecimal unitPrice,
            int availableQty,
            boolean deleted
    ) {}

    public record CapturePayment(
            String orderId,
            String sagaId,
            BigDecimal amount,
            String currency,
            String paymentIdempotencyKey
    ) {}

    public record RefundPayment(String orderId, String sagaId, String paymentId, BigDecimal amount) {}

    public record PaymentCaptured(String orderId, String sagaId, String paymentId, BigDecimal amount) {}

    public record PaymentFailed(String orderId, String sagaId, String reason) {}

    public record PaymentRefunded(String orderId, String sagaId, String paymentId) {}

    public record CreateShipment(
            String orderId,
            String sagaId,
            String customerId,
            List<OrderLine> lines,
            ShippingAddress shippingAddress
    ) {
        /** Backward-compatible constructor used by older producers. */
        public CreateShipment(String orderId, String sagaId, String customerId, List<OrderLine> lines) {
            this(orderId, sagaId, customerId, lines, null);
        }
    }

    public record ShippingAddress(
            String recipientName,
            String line1,
            String line2,
            String city,
            String state,
            String postalCode,
            String country
    ) {}

    public record ShipmentCreated(
            String orderId,
            String sagaId,
            String shipmentId,
            String trackingNumber,
            String carrier,
            String labelUrl
    ) {
        public ShipmentCreated(String orderId, String sagaId, String shipmentId, String trackingNumber) {
            this(orderId, sagaId, shipmentId, trackingNumber, null, null);
        }
    }

    public record ShipmentFailed(String orderId, String sagaId, String reason) {}

    public record ShipmentStatusUpdated(
            String orderId,
            String sagaId,
            String shipmentId,
            String status,
            String trackingNumber,
            String message
    ) {}

    public record InvoiceIssued(String orderId, String invoiceId, String invoiceNumber) {}

    public record SagaStepCompleted(String sagaId, String orderId, String step, String status) {}

    public record SagaTerminal(String sagaId, String orderId, String status, String reason) {}
}
