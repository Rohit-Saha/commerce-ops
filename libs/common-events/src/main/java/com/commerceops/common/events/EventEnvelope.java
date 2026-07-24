package com.commerceops.common.events;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;
import java.util.UUID;

@JsonInclude(JsonInclude.Include.NON_NULL)
public record EventEnvelope(
        String eventId,
        String eventType,
        String aggregateId,
        String correlationId,
        String causationId,
        String sagaId,
        String idempotencyKey,
        Instant occurredAt,
        JsonNode payload
) {
    public static EventEnvelope of(String eventType, String aggregateId, String correlationId,
                                   String causationId, String sagaId, String idempotencyKey, JsonNode payload) {
        return new EventEnvelope(
                UUID.randomUUID().toString(),
                eventType,
                aggregateId,
                correlationId,
                causationId,
                sagaId,
                idempotencyKey,
                Instant.now(),
                payload
        );
    }
}
