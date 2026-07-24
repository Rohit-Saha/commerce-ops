package com.commerceops.inventory.messaging;

import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import com.commerceops.common.events.EventTypes;
import com.commerceops.common.events.Payloads;
import com.commerceops.common.events.Topics;
import com.commerceops.common.idempotency.IdempotencyService;
import com.commerceops.common.kafka.OutboxService;
import com.commerceops.inventory.service.InventoryService;
import com.commerceops.inventory.service.exception.InsufficientStockException;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Consumes {@link Topics#INVENTORY_COMMANDS} (ReserveInventory / ReleaseInventory) and
 * publishes the resulting domain events to {@link Topics#INVENTORY_EVENTS} via the outbox.
 */
@Component
public class InventoryCommandListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryCommandListener.class);
    private static final String CONSUMER_GROUP = "inventory-service";

    private final InventoryService inventoryService;
    private final IdempotencyService idempotencyService;
    private final OutboxService outboxService;

    public InventoryCommandListener(InventoryService inventoryService,
                                     IdempotencyService idempotencyService,
                                     OutboxService outboxService) {
        this.inventoryService = inventoryService;
        this.idempotencyService = idempotencyService;
        this.outboxService = outboxService;
    }

    @KafkaListener(topics = Topics.INVENTORY_COMMANDS, groupId = CONSUMER_GROUP)
    public void onCommand(String message) {
        EventEnvelope envelope;
        try {
            envelope = EventJson.read(message, EventEnvelope.class);
        } catch (Exception ex) {
            log.error("Failed to parse command envelope, dropping message: {}", message, ex);
            return;
        }

        if (!idempotencyService.markIfNew(CONSUMER_GROUP, envelope.eventId())) {
            log.debug("Skipping already processed command {} ({})", envelope.eventId(), envelope.eventType());
            return;
        }

        try {
            switch (envelope.eventType()) {
                case EventTypes.RESERVE_INVENTORY -> handleReserve(envelope);
                case EventTypes.RELEASE_INVENTORY -> handleRelease(envelope);
                default -> log.debug("Ignoring inventory command type {}", envelope.eventType());
            }
        } catch (Exception ex) {
            log.error("Failed to process command {} ({})", envelope.eventId(), envelope.eventType(), ex);
        }
    }

    private void handleReserve(EventEnvelope envelope) {
        Payloads.ReserveInventory command = EventJson.fromNode(envelope.payload(), Payloads.ReserveInventory.class);
        try {
            String reservationId = inventoryService.reserve(command.orderId(), command.sagaId(), command.lines());
            Payloads.InventoryReserved payload =
                    new Payloads.InventoryReserved(command.orderId(), command.sagaId(), reservationId);
            publish(EventTypes.INVENTORY_RESERVED, command.orderId(), command.sagaId(), envelope.eventId(), payload);
        } catch (InsufficientStockException ex) {
            log.warn("Inventory reservation failed for order={}: {}", command.orderId(), ex.getMessage());
            Payloads.InventoryReserveFailed payload =
                    new Payloads.InventoryReserveFailed(command.orderId(), command.sagaId(), ex.getMessage());
            publish(EventTypes.INVENTORY_RESERVE_FAILED, command.orderId(), command.sagaId(), envelope.eventId(), payload);
        }
    }

    private void handleRelease(EventEnvelope envelope) {
        Payloads.ReleaseInventory command = EventJson.fromNode(envelope.payload(), Payloads.ReleaseInventory.class);
        String reservationId = inventoryService.release(command.orderId(), command.reservationId());
        Payloads.InventoryReleased payload =
                new Payloads.InventoryReleased(command.orderId(), command.sagaId(), reservationId);
        publish(EventTypes.INVENTORY_RELEASED, command.orderId(), command.sagaId(), envelope.eventId(), payload);
    }

    private void publish(String eventType, String orderId, String sagaId, String causationId, Object payload) {
        EventEnvelope envelope = EventEnvelope.of(
                eventType,
                orderId,
                sagaId,
                causationId,
                sagaId,
                null,
                EventJson.toNode(payload));
        outboxService.enqueue(Topics.INVENTORY_EVENTS, envelope);
    }
}
