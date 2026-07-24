package com.commerceops.catalog.messaging;

import com.commerceops.catalog.service.CatalogProjectionService;
import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import com.commerceops.common.events.EventTypes;
import com.commerceops.common.events.Payloads;
import com.commerceops.common.events.Topics;
import com.commerceops.common.idempotency.IdempotencyService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

@Component
public class StockItemChangedListener {

    private static final Logger log = LoggerFactory.getLogger(StockItemChangedListener.class);
    private static final String CONSUMER_GROUP = "catalog-service";

    private final IdempotencyService idempotencyService;
    private final CatalogProjectionService projectionService;

    public StockItemChangedListener(IdempotencyService idempotencyService,
                                    CatalogProjectionService projectionService) {
        this.idempotencyService = idempotencyService;
        this.projectionService = projectionService;
    }

    @KafkaListener(topics = Topics.INVENTORY_EVENTS, groupId = CONSUMER_GROUP)
    @Transactional
    public void onMessage(String message) {
        EventEnvelope envelope;
        try {
            envelope = EventJson.read(message, EventEnvelope.class);
        } catch (Exception ex) {
            log.error("Failed to parse inventory event, dropping: {}", message, ex);
            return;
        }

        if (!EventTypes.STOCK_ITEM_CHANGED.equals(envelope.eventType())) {
            return;
        }

        if (!idempotencyService.markIfNew(CONSUMER_GROUP, envelope.eventId())) {
            log.debug("Skipping already-processed StockItemChanged {}", envelope.eventId());
            return;
        }

        Payloads.StockItemChanged payload =
                EventJson.fromNode(envelope.payload(), Payloads.StockItemChanged.class);
        projectionService.applyStockItemChanged(payload);
    }
}
