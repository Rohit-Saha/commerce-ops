package com.commerceops.common.idempotency;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
public class IdempotencyService {

    private final ProcessedEventRepository repository;

    public IdempotencyService(ProcessedEventRepository repository) {
        this.repository = repository;
    }

    @Transactional
    public boolean markIfNew(String consumerGroup, String eventId) {
        ProcessedEvent.Pk pk = new ProcessedEvent.Pk(consumerGroup, eventId);
        if (repository.existsById(pk)) {
            return false;
        }
        try {
            repository.saveAndFlush(new ProcessedEvent(consumerGroup, eventId));
            return true;
        } catch (DataIntegrityViolationException ex) {
            return false;
        }
    }
}
