package com.commerceops.common.idempotency;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.IdClass;
import jakarta.persistence.Table;

import java.io.Serializable;
import java.time.Instant;
import java.util.Objects;

@Entity
@Table(name = "processed_events")
@IdClass(ProcessedEvent.Pk.class)
public class ProcessedEvent {

    @Id
    @Column(name = "consumer_group", nullable = false)
    private String consumerGroup;

    @Id
    @Column(name = "event_id", nullable = false)
    private String eventId;

    @Column(name = "processed_at", nullable = false)
    private Instant processedAt = Instant.now();

    public ProcessedEvent() {}

    public ProcessedEvent(String consumerGroup, String eventId) {
        this.consumerGroup = consumerGroup;
        this.eventId = eventId;
    }

    public String getConsumerGroup() { return consumerGroup; }
    public String getEventId() { return eventId; }
    public Instant getProcessedAt() { return processedAt; }

    public static class Pk implements Serializable {
        private String consumerGroup;
        private String eventId;

        public Pk() {}

        public Pk(String consumerGroup, String eventId) {
            this.consumerGroup = consumerGroup;
            this.eventId = eventId;
        }

        @Override
        public boolean equals(Object o) {
            if (this == o) return true;
            if (!(o instanceof Pk pk)) return false;
            return Objects.equals(consumerGroup, pk.consumerGroup) && Objects.equals(eventId, pk.eventId);
        }

        @Override
        public int hashCode() {
            return Objects.hash(consumerGroup, eventId);
        }
    }
}
