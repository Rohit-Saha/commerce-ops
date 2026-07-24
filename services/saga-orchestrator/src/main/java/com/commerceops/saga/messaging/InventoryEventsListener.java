package com.commerceops.saga.messaging;

import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import com.commerceops.common.events.EventTypes;
import com.commerceops.common.events.Topics;
import com.commerceops.common.idempotency.IdempotencyService;
import com.commerceops.saga.SagaConstants;
import com.commerceops.saga.service.SagaLifecycleService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class InventoryEventsListener {

    private static final Logger log = LoggerFactory.getLogger(InventoryEventsListener.class);

    private final IdempotencyService idempotencyService;
    private final SagaLifecycleService lifecycleService;

    public InventoryEventsListener(IdempotencyService idempotencyService, SagaLifecycleService lifecycleService) {
        this.idempotencyService = idempotencyService;
        this.lifecycleService = lifecycleService;
    }

    @KafkaListener(topics = Topics.INVENTORY_EVENTS, groupId = SagaConstants.IDEMPOTENCY_GROUP)
    @Transactional
    public void onMessage(String message) {
        EventEnvelope envelope = EventJson.read(message, EventEnvelope.class);
        if (!idempotencyService.markIfNew(SagaConstants.IDEMPOTENCY_GROUP, envelope.eventId())) {
            log.debug("Skipping already-processed event {} ({})", envelope.eventId(), envelope.eventType());
            return;
        }

        switch (envelope.eventType()) {
            case EventTypes.INVENTORY_RESERVED -> lifecycleService.handleInventoryReserved(envelope);
            case EventTypes.INVENTORY_RESERVE_FAILED -> lifecycleService.handleInventoryReserveFailed(envelope);
            case EventTypes.INVENTORY_RELEASED -> lifecycleService.handleInventoryReleased(envelope);
            default -> log.debug("Ignoring event type {} on {}", envelope.eventType(), Topics.INVENTORY_EVENTS);
        }
    }
}
