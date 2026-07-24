package com.commerceops.common.kafka;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.List;

@Component
public class OutboxRelay {

    private static final Logger log = LoggerFactory.getLogger(OutboxRelay.class);

    private final OutboxEventRepository repository;
    private final KafkaTemplate<String, String> kafkaTemplate;

    public OutboxRelay(OutboxEventRepository repository, KafkaTemplate<String, String> kafkaTemplate) {
        this.repository = repository;
        this.kafkaTemplate = kafkaTemplate;
    }

    @Scheduled(fixedDelayString = "${commerce.outbox.poll-ms:500}")
    @Transactional
    public void publishPending() {
        List<OutboxEvent> batch = repository.findPendingBatch();
        for (OutboxEvent event : batch) {
            try {
                kafkaTemplate.send(event.getTopic(), event.getAggregateId(), event.getPayload()).get();
                event.setStatus("PUBLISHED");
                event.setPublishedAt(Instant.now());
                repository.save(event);
            } catch (Exception ex) {
                log.warn("Failed to publish outbox id={} topic={}: {}", event.getId(), event.getTopic(), ex.getMessage());
            }
        }
    }
}
