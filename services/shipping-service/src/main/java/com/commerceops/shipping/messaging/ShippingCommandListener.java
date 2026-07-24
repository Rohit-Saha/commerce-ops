package com.commerceops.shipping.messaging;

import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import com.commerceops.common.events.EventTypes;
import com.commerceops.common.events.Payloads;
import com.commerceops.common.events.Topics;
import com.commerceops.common.idempotency.IdempotencyService;
import com.commerceops.shipping.service.ShippingService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ShippingCommandListener {

    private static final Logger log = LoggerFactory.getLogger(ShippingCommandListener.class);
    private static final String CONSUMER_GROUP = "shipping-service";

    private final IdempotencyService idempotencyService;
    private final ShippingService shippingService;

    public ShippingCommandListener(IdempotencyService idempotencyService, ShippingService shippingService) {
        this.idempotencyService = idempotencyService;
        this.shippingService = shippingService;
    }

    @KafkaListener(topics = Topics.SHIPPING_COMMANDS, groupId = CONSUMER_GROUP)
    public void onMessage(String message) {
        EventEnvelope envelope = EventJson.read(message, EventEnvelope.class);

        if (!idempotencyService.markIfNew(CONSUMER_GROUP, envelope.eventId())) {
            log.info("Duplicate command eventId={} eventType={} skipped", envelope.eventId(), envelope.eventType());
            return;
        }

        switch (envelope.eventType()) {
            case EventTypes.CREATE_SHIPMENT -> {
                Payloads.CreateShipment cmd = EventJson.fromNode(envelope.payload(), Payloads.CreateShipment.class);
                shippingService.createShipment(cmd, envelope.correlationId(), envelope.eventId());
            }
            default -> log.warn("Unhandled event type={} on topic={}", envelope.eventType(), Topics.SHIPPING_COMMANDS);
        }
    }
}
