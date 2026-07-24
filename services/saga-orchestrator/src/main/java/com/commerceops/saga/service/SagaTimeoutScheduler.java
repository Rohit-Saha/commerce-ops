package com.commerceops.saga.service;

import com.commerceops.saga.domain.SagaInstance;
import com.commerceops.saga.domain.SagaStatus;
import com.commerceops.saga.repository.SagaInstanceRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.Instant;
import java.util.List;

/**
 * Sweeps for sagas stuck past their step deadline and either escalates them to
 * FAILED_NEEDS_ATTENTION or kicks off compensation, depending on how far the saga progressed.
 */
@Component
public class SagaTimeoutScheduler {

    private static final Logger log = LoggerFactory.getLogger(SagaTimeoutScheduler.class);

    private static final List<SagaStatus> ACTIVE_STEP_STATUSES = List.of(
            SagaStatus.RESERVING, SagaStatus.PAYING, SagaStatus.SHIPPING, SagaStatus.COMPENSATING);

    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaLifecycleService lifecycleService;

    public SagaTimeoutScheduler(SagaInstanceRepository sagaInstanceRepository, SagaLifecycleService lifecycleService) {
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.lifecycleService = lifecycleService;
    }

    @Scheduled(fixedDelayString = "${commerce.saga.timeout-scan-ms:5000}")
    public void checkTimeouts() {
        List<SagaInstance> expired = sagaInstanceRepository.findByStepDeadlineBeforeAndStatusIn(Instant.now(), ACTIVE_STEP_STATUSES);
        for (SagaInstance saga : expired) {
            Long sagaId = saga.getId();
            try {
                lifecycleService.handleTimeout(sagaId);
            } catch (Exception ex) {
                log.error("Failed to process timeout for saga {}: {}", sagaId, ex.getMessage(), ex);
            }
        }
    }
}
