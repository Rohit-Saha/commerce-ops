package com.commerceops.shipping.repository;

import com.commerceops.shipping.domain.Shipment;
import com.commerceops.shipping.domain.ShipmentStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Collection;
import java.util.List;
import java.util.Optional;

public interface ShipmentRepository extends JpaRepository<Shipment, Long> {

    List<Shipment> findByOrderIdOrderByCreatedAtDesc(String orderId);

    Optional<Shipment> findFirstByOrderIdAndStatusNotInOrderByCreatedAtDesc(
            String orderId, Collection<ShipmentStatus> statuses);

    Optional<Shipment> findByCarrierOrderId(String carrierOrderId);

    Optional<Shipment> findByTrackingNumber(String trackingNumber);
}
