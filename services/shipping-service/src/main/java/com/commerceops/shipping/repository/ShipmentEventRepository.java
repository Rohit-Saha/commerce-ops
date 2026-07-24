package com.commerceops.shipping.repository;

import com.commerceops.shipping.domain.ShipmentEvent;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;

public interface ShipmentEventRepository extends JpaRepository<ShipmentEvent, Long> {
    List<ShipmentEvent> findByShipmentIdOrderByOccurredAtAsc(Long shipmentId);
}
