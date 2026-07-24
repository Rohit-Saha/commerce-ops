package com.commerceops.order.repository;

import com.commerceops.order.domain.ClientIdempotency;
import org.springframework.data.jpa.repository.JpaRepository;

public interface ClientIdempotencyRepository extends JpaRepository<ClientIdempotency, String> {
}
