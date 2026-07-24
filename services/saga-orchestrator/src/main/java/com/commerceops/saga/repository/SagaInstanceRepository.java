package com.commerceops.saga.repository;

import com.commerceops.saga.domain.SagaInstance;
import com.commerceops.saga.domain.SagaStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface SagaInstanceRepository extends JpaRepository<SagaInstance, Long> {

    Optional<SagaInstance> findByOrderId(String orderId);

    List<SagaInstance> findByStepDeadlineBeforeAndStatusIn(Instant deadline, Collection<SagaStatus> statuses);
}
