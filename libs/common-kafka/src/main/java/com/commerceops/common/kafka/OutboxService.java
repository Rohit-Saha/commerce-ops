package com.commerceops.common.kafka;

import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class OutboxService {

    private final OutboxEventRepository repository;

    public OutboxService(OutboxEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public void enqueue(String topic, EventEnvelope envelope) {
        OutboxEvent event = new OutboxEvent();
        event.setTopic(topic);
        event.setAggregateId(envelope.aggregateId());
        event.setEventType(envelope.eventType());
        event.setPayload(EventJson.write(envelope));
        repository.save(event);
    }
}
