package com.commerceops.invoice.messaging;

import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import com.commerceops.common.events.EventTypes;
import com.commerceops.common.events.Payloads;
import com.commerceops.common.events.Topics;
import com.commerceops.common.idempotency.IdempotencyService;
import com.commerceops.invoice.service.InvoiceService;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.annotation.KafkaListener;
import org.springframework.stereotype.Component;

@Component
public class ShippingEventListener {

    private static final Logger log = LoggerFactory.getLogger(ShippingEventListener.class);
    private static final String CONSUMER_GROUP = "invoice-service";

    private final IdempotencyService idempotencyService;
    private final InvoiceService invoiceService;

    public ShippingEventListener(IdempotencyService idempotencyService, InvoiceService invoiceService) {
        this.idempotencyService = idempotencyService;
        this.invoiceService = invoiceService;
    }

    @KafkaListener(topics = Topics.SHIPPING_EVENTS, groupId = CONSUMER_GROUP)
    public void onMessage(String message) {
        EventEnvelope envelope = EventJson.read(message, EventEnvelope.class);

        if (!EventTypes.SHIPMENT_CREATED.equals(envelope.eventType())) {
            return;
        }

        if (!idempotencyService.markIfNew(CONSUMER_GROUP, envelope.eventId())) {
            log.info("Duplicate ShipmentCreated eventId={} skipped", envelope.eventId());
            return;
        }

        Payloads.ShipmentCreated payload = EventJson.fromNode(envelope.payload(), Payloads.ShipmentCreated.class);
        try {
            invoiceService.issueForShipment(payload, envelope.correlationId(), envelope.eventId());
        } catch (RuntimeException ex) {
            log.error("Failed to issue invoice for orderId={}: {}", payload.orderId(), ex.getMessage(), ex);
            throw ex;
        }
    }
}
