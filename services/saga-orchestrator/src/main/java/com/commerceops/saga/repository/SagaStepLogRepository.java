package com.commerceops.saga.repository;

import com.commerceops.saga.domain.SagaStepLog;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface SagaStepLogRepository extends JpaRepository<SagaStepLog, Long> {

    List<SagaStepLog> findBySagaIdOrderByIdAsc(Long sagaId);
}
