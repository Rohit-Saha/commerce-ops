package com.commerceops.saga.service;

import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import com.commerceops.common.events.EventTypes;
import com.commerceops.common.events.Payloads;
import com.commerceops.common.events.Topics;
import com.commerceops.common.kafka.OutboxService;
import com.commerceops.saga.domain.SagaInstance;
import org.springframework.stereotype.Component;

import java.util.UUID;

/**
 * Publishes outbound commands and saga.events notifications through the transactional outbox,
 * keyed consistently by orderId so downstream consumers can order/partition on it.
 */
@Component
public class SagaCommandPublisher {

    private final OutboxService outboxService;

    public SagaCommandPublisher(OutboxService outboxService) {
        this.outboxService = outboxService;
    }

    public void sendCommand(String topic, String eventType, SagaInstance saga, Object payload, EventEnvelope causation) {
        EventEnvelope envelope = EventEnvelope.of(
                eventType,
                saga.getOrderId(),
                saga.getOrderId(),
                causation != null ? causation.eventId() : null,
                saga.sagaId(),
                UUID.randomUUID().toString(),
                EventJson.toNode(payload)
        );
        outboxService.enqueue(topic, envelope);
    }

    public void publishStepCompleted(SagaInstance saga, String step, EventEnvelope causation) {
        sendCommand(Topics.SAGA_EVENTS,
                EventTypes.SAGA_STEP_COMPLETED,
                saga,
                new Payloads.SagaStepCompleted(saga.sagaId(), saga.getOrderId(), step, "COMPLETED"),
                causation);
    }

    public void publishTerminal(SagaInstance saga, String eventType, String outcomeStatus, String reason, EventEnvelope causation) {
        sendCommand(Topics.SAGA_EVENTS,
                eventType,
                saga,
                new Payloads.SagaTerminal(saga.sagaId(), saga.getOrderId(), outcomeStatus, reason),
                causation);
    }
}
