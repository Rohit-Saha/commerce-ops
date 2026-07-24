package com.commerceops.gateway.messaging;

import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import com.commerceops.common.events.Topics;
import com.commerceops.gateway.service.SseBroadcastService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

/**
 * Fans every domain-event topic out to connected SSE clients so the admin UI gets live
 * order/saga/inventory/payment/shipping updates without polling.
 */
@Component
public class LiveUpdateListener {

    private static final Logger log = LoggerFactory.getLogger(LiveUpdateListener.class);
    private static final String CONSUMER_GROUP = "api-gateway";
    private static final String SSE_EVENT_NAME = "order-update";

    private final SseBroadcastService broadcastService;

    public LiveUpdateListener(SseBroadcastService broadcastService) {
        this.broadcastService = broadcastService;
    }

    @KafkaListener(topics = Topics.ORDER_EVENTS, groupId = CONSUMER_GROUP)
    public void onOrderEvent(String message) {
        broadcast(message);
    }

    @KafkaListener(topics = Topics.SAGA_EVENTS, groupId = CONSUMER_GROUP)
    public void onSagaEvent(String message) {
        broadcast(message);
    }

    @KafkaListener(topics = Topics.INVENTORY_EVENTS, groupId = CONSUMER_GROUP)
    public void onInventoryEvent(String message) {
        broadcast(message);
    }

    @KafkaListener(topics = Topics.PAYMENT_EVENTS, groupId = CONSUMER_GROUP)
    public void onPaymentEvent(String message) {
        broadcast(message);
    }

    @KafkaListener(topics = Topics.SHIPPING_EVENTS, groupId = CONSUMER_GROUP)
    public void onShippingEvent(String message) {
        broadcast(message);
    }

    private void broadcast(String message) {
        EventEnvelope envelope;
        try {
            envelope = EventJson.read(message, EventEnvelope.class);
        } catch (Exception ex) {
            log.warn("Failed to parse event envelope for SSE broadcast, dropping: {}", message, ex);
            return;
        }
        broadcastService.broadcast(SSE_EVENT_NAME, envelope);
    }
}
