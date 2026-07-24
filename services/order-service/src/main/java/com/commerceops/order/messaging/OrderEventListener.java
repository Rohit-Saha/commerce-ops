package com.commerceops.order.messaging;

import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import com.commerceops.common.events.EventTypes;
import com.commerceops.common.events.Payloads;
import com.commerceops.common.events.Topics;
import com.commerceops.common.idempotency.IdempotencyService;
import com.commerceops.order.domain.OrderStatus;
import com.commerceops.order.service.OrderService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

import java.util.function.Consumer;

/**
 * Consumes downstream domain events from inventory, payment, shipping and the saga
 * orchestrator and projects them onto the order's status.
 */
@Component
public class OrderEventListener {

    private static final Logger log = LoggerFactory.getLogger(OrderEventListener.class);
    private static final String CONSUMER_GROUP = "order-service";

    private final OrderService orderService;
    private final IdempotencyService idempotencyService;

    public OrderEventListener(OrderService orderService, IdempotencyService idempotencyService) {
        this.orderService = orderService;
        this.idempotencyService = idempotencyService;
    }

    @KafkaListener(topics = Topics.INVENTORY_EVENTS, groupId = CONSUMER_GROUP)
    public void onInventoryEvent(String message) {
        handle(message, this::applyInventoryEvent);
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = CONSUMER_GROUP)
    public void onPaymentEvent(String message) {
        handle(message, this::applyPaymentEvent);
    }

    @KafkaListener(topics = Topics.SHIPPING_EVENTS, groupId = CONSUMER_GROUP)
    public void onShippingEvent(String message) {
        handle(message, this::applyShippingEvent);
    }

    @KafkaListener(topics = Topics.SAGA_EVENTS, groupId = CONSUMER_GROUP)
    public void onSagaEvent(String message) {
        handle(message, this::applySagaEvent);
    }

    private void handle(String message, Consumer<EventEnvelope> handler) {
        EventEnvelope envelope;
        try {
            envelope = EventJson.read(message, EventEnvelope.class);
        } catch (Exception ex) {
            log.error("Failed to parse event envelope, dropping message: {}", message, ex);
            return;
        }

        if (!idempotencyService.markIfNew(CONSUMER_GROUP, envelope.eventId())) {
            log.debug("Skipping already processed event {} ({})", envelope.eventId(), envelope.eventType());
            return;
        }

        try {
            handler.accept(envelope);
        } catch (Exception ex) {
            log.error("Failed to apply event {} ({}) to order state", envelope.eventId(), envelope.eventType(), ex);
        }
    }

    private void applyInventoryEvent(EventEnvelope envelope) {
        switch (envelope.eventType()) {
            case EventTypes.INVENTORY_RESERVED -> {
                var payload = EventJson.fromNode(envelope.payload(), Payloads.InventoryReserved.class);
                orderService.updateStatus(payload.orderId(), OrderStatus.RESERVED, "Inventory reserved");
            }
            case EventTypes.INVENTORY_RESERVE_FAILED -> {
                var payload = EventJson.fromNode(envelope.payload(), Payloads.InventoryReserveFailed.class);
                orderService.updateStatus(payload.orderId(), OrderStatus.FAILED, payload.reason());
            }
            case EventTypes.INVENTORY_RELEASED -> {
                var payload = EventJson.fromNode(envelope.payload(), Payloads.InventoryReleased.class);
                orderService.updateStatus(payload.orderId(), OrderStatus.CANCELLED, "Inventory released");
            }
            default -> log.debug("Ignoring inventory event type {}", envelope.eventType());
        }
    }

    private void applyPaymentEvent(EventEnvelope envelope) {
        switch (envelope.eventType()) {
            case EventTypes.PAYMENT_CAPTURED -> {
                var payload = EventJson.fromNode(envelope.payload(), Payloads.PaymentCaptured.class);
                orderService.updateStatus(payload.orderId(), OrderStatus.PAID, "Payment captured");
            }
            case EventTypes.PAYMENT_FAILED -> {
                var payload = EventJson.fromNode(envelope.payload(), Payloads.PaymentFailed.class);
                orderService.updateStatus(payload.orderId(), OrderStatus.FAILED, payload.reason());
            }
            case EventTypes.PAYMENT_REFUNDED -> {
                var payload = EventJson.fromNode(envelope.payload(), Payloads.PaymentRefunded.class);
                orderService.updateStatus(payload.orderId(), OrderStatus.CANCELLED, "Payment refunded");
            }
            default -> log.debug("Ignoring payment event type {}", envelope.eventType());
        }
    }

    private void applyShippingEvent(EventEnvelope envelope) {
        switch (envelope.eventType()) {
            case EventTypes.SHIPMENT_CREATED -> {
                var payload = EventJson.fromNode(envelope.payload(), Payloads.ShipmentCreated.class);
                orderService.updateStatus(payload.orderId(), OrderStatus.SHIPPING, "Shipment created");
            }
            case EventTypes.SHIPMENT_FAILED -> {
                var payload = EventJson.fromNode(envelope.payload(), Payloads.ShipmentFailed.class);
                orderService.updateStatus(payload.orderId(), OrderStatus.FAILED, payload.reason());
            }
            case EventTypes.SHIPMENT_STATUS_UPDATED -> {
                var payload = EventJson.fromNode(envelope.payload(), Payloads.ShipmentStatusUpdated.class);
                if ("DELIVERED".equalsIgnoreCase(payload.status())) {
                    orderService.updateStatus(payload.orderId(), OrderStatus.DELIVERED, "Shipment delivered");
                }
            }
            default -> log.debug("Ignoring shipping event type {}", envelope.eventType());
        }
    }

    private void applySagaEvent(EventEnvelope envelope) {
        switch (envelope.eventType()) {
            case EventTypes.SAGA_STEP_COMPLETED -> {
                var payload = EventJson.fromNode(envelope.payload(), Payloads.SagaStepCompleted.class);
                applySagaStepStarted(payload);
            }
            case EventTypes.SAGA_COMPLETED -> {
                // Order progression after payment is owned by shipping events (SHIPPING → DELIVERED).
                log.debug("Ignoring SagaCompleted for order status projection: {}", envelope.aggregateId());
            }
            case EventTypes.SAGA_COMPENSATED -> {
                var payload = EventJson.fromNode(envelope.payload(), Payloads.SagaTerminal.class);
                orderService.updateStatus(payload.orderId(), OrderStatus.CANCELLED, payload.reason());
            }
            case EventTypes.SAGA_FAILED -> {
                var payload = EventJson.fromNode(envelope.payload(), Payloads.SagaTerminal.class);
                orderService.updateStatus(payload.orderId(), OrderStatus.FAILED, payload.reason());
            }
            default -> log.debug("Ignoring saga event type {}", envelope.eventType());
        }
    }

    private void applySagaStepStarted(Payloads.SagaStepCompleted payload) {
        if (!"STARTED".equalsIgnoreCase(payload.status())) {
            return;
        }
        OrderStatus target = switch (payload.step()) {
            case "RESERVE_INVENTORY" -> OrderStatus.RESERVING;
            case "CAPTURE_PAYMENT" -> OrderStatus.PAYMENT_PENDING;
            default -> null;
        };
        if (target != null) {
            orderService.updateStatus(payload.orderId(), target, "Saga step " + payload.step() + " started");
        }
    }
}
