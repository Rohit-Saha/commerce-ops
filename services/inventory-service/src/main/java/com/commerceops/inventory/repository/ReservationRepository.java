package com.commerceops.inventory.repository;

import com.commerceops.inventory.domain.Reservation;
import com.commerceops.inventory.domain.ReservationStatus;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.Optional;

public interface ReservationRepository extends JpaRepository<Reservation, String> {

    Optional<Reservation> findFirstByOrderIdAndStatusOrderByCreatedAtDesc(String orderId, ReservationStatus status);
}
