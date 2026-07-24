package com.commerceops.saga.service;

import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import com.commerceops.common.events.EventTypes;
import com.commerceops.common.events.Payloads;
import com.commerceops.common.events.Topics;
import com.commerceops.saga.domain.SagaInstance;
import com.commerceops.saga.domain.SagaStatus;
import com.commerceops.saga.domain.SagaStepLog;
import com.commerceops.saga.repository.SagaInstanceRepository;
import com.commerceops.saga.repository.SagaStepLogRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * Core state machine for the OrderCreated -> ReserveInventory -> CapturePayment -> (await admin ship) saga,
 * including compensations. All handlers are idempotent by design (guarded by the caller's
 * IdempotencyService check plus status/step guards below), so re-delivered or out-of-order events are
 * safely ignored rather than double-applied.
 */
@Service
public class SagaLifecycleService {

    private static final Logger log = LoggerFactory.getLogger(SagaLifecycleService.class);
    private static final String AWAIT_SHIPMENT = "AWAIT_SHIPMENT";

    private final SagaInstanceRepository sagaInstanceRepository;
    private final SagaStepLogRepository sagaStepLogRepository;
    private final SagaCommandPublisher publisher;
    private final int stepTimeoutSeconds;
    private final int maxRetries;

    public SagaLifecycleService(SagaInstanceRepository sagaInstanceRepository,
                                 SagaStepLogRepository sagaStepLogRepository,
                                 SagaCommandPublisher publisher,
                                 @Value("${commerce.saga.step-timeout-seconds:30}") int stepTimeoutSeconds,
                                 @Value("${commerce.saga.max-retries:3}") int maxRetries) {
        this.sagaInstanceRepository = sagaInstanceRepository;
        this.sagaStepLogRepository = sagaStepLogRepository;
        this.publisher = publisher;
        this.stepTimeoutSeconds = stepTimeoutSeconds;
        this.maxRetries = maxRetries;
    }

    // ---------------------------------------------------------------- OrderCreated / OrderCancelRequested

    @Transactional
    public void handleOrderCreated(EventEnvelope envelope) {
        Payloads.OrderCreated payload = EventJson.fromNode(envelope.payload(), Payloads.OrderCreated.class);

        if (sagaInstanceRepository.findByOrderId(payload.orderId()).isPresent()) {
            log.warn("Saga already exists for orderId={}, ignoring duplicate OrderCreated", payload.orderId());
            return;
        }

        SagaInstance saga = new SagaInstance();
        saga.setOrderId(payload.orderId());
        saga.setCustomerId(payload.customerId());
        saga.setStatus(SagaStatus.STARTED);
        saga.setPayloadJson(EventJson.write(payload));
        saga.setRetryCount(0);
        saga = sagaInstanceRepository.save(saga);
        recordStep(saga, EventTypes.ORDER_CREATED, "COMPLETED", "Saga started for order " + payload.orderId());

        Payloads.ReserveInventory command = new Payloads.ReserveInventory(payload.orderId(), saga.sagaId(), payload.lines());
        publisher.sendCommand(Topics.INVENTORY_COMMANDS, EventTypes.RESERVE_INVENTORY, saga, command, envelope);

        saga.setStatus(SagaStatus.RESERVING);
        saga.setCurrentStep(EventTypes.RESERVE_INVENTORY);
        saga.setStepDeadline(Instant.now().plusSeconds(stepTimeoutSeconds));
        recordStep(saga, EventTypes.RESERVE_INVENTORY, "SENT", "Reservation command dispatched");
    }

    @Transactional
    public void handleOrderCancelRequested(EventEnvelope envelope) {
        Payloads.OrderCancelRequested payload = EventJson.fromNode(envelope.payload(), Payloads.OrderCancelRequested.class);
        SagaInstance saga = sagaInstanceRepository.findByOrderId(payload.orderId()).orElse(null);
        if (saga == null) {
            log.warn("No saga found for orderId={} on OrderCancelRequested", payload.orderId());
            return;
        }
        if (saga.getStatus().isTerminal() || saga.getStatus() == SagaStatus.COMPENSATING) {
            log.info("Ignoring OrderCancelRequested for saga {} already in status {}", saga.getId(), saga.getStatus());
            return;
        }

        saga.setLastError("Cancellation requested: " + payload.reason());
        recordStep(saga, EventTypes.ORDER_CANCEL_REQUESTED, "RECEIVED", payload.reason());

        if (saga.getPaymentId() != null) {
            startRefundThenRelease(saga, envelope, "Cancellation requested: " + payload.reason());
        } else if (saga.getReservationId() != null) {
            startReleaseOnly(saga, envelope, "Cancellation requested: " + payload.reason());
        } else {
            saga.setStatus(SagaStatus.COMPENSATED);
            saga.setCurrentStep(null);
            saga.setStepDeadline(null);
            saga.setRetryCount(0);
            publisher.publishTerminal(saga, EventTypes.SAGA_COMPENSATED, SagaStatus.COMPENSATED.name(),
                    "Cancelled before any resources were reserved", envelope);
        }
    }

    // ---------------------------------------------------------------------------------- Inventory events

    @Transactional
    public void handleInventoryReserved(EventEnvelope envelope) {
        Payloads.InventoryReserved payload = EventJson.fromNode(envelope.payload(), Payloads.InventoryReserved.class);
        SagaInstance saga = findSaga(payload.sagaId(), payload.orderId());
        if (saga == null) return;
        if (saga.getStatus() != SagaStatus.RESERVING) {
            log.info("Ignoring InventoryReserved for saga {} in status {}", saga.getId(), saga.getStatus());
            return;
        }

        saga.setReservationId(payload.reservationId());
        saga.setStatus(SagaStatus.RESERVED);
        recordStep(saga, EventTypes.INVENTORY_RESERVED, "COMPLETED", "reservationId=" + payload.reservationId());
        publisher.publishStepCompleted(saga, EventTypes.RESERVE_INVENTORY, envelope);

        Payloads.OrderCreated original = EventJson.read(saga.getPayloadJson(), Payloads.OrderCreated.class);
        Payloads.CapturePayment command = new Payloads.CapturePayment(
                saga.getOrderId(), saga.sagaId(), original.totalAmount(), original.currency(), saga.getOrderId());
        publisher.sendCommand(Topics.PAYMENT_COMMANDS, EventTypes.CAPTURE_PAYMENT, saga, command, envelope);

        saga.setStatus(SagaStatus.PAYING);
        saga.setCurrentStep(EventTypes.CAPTURE_PAYMENT);
        saga.setStepDeadline(Instant.now().plusSeconds(stepTimeoutSeconds));
        saga.setRetryCount(0);
        recordStep(saga, EventTypes.CAPTURE_PAYMENT, "SENT", "Capture payment command dispatched");
    }

    @Transactional
    public void handleInventoryReserveFailed(EventEnvelope envelope) {
        Payloads.InventoryReserveFailed payload = EventJson.fromNode(envelope.payload(), Payloads.InventoryReserveFailed.class);
        SagaInstance saga = findSaga(payload.sagaId(), payload.orderId());
        if (saga == null) return;
        if (saga.getStatus().isTerminal()) {
            log.info("Ignoring InventoryReserveFailed for saga {} already terminal ({})", saga.getId(), saga.getStatus());
            return;
        }
        if (saga.getStatus() != SagaStatus.RESERVING) {
            log.info("Ignoring InventoryReserveFailed for saga {} in status {}", saga.getId(), saga.getStatus());
            return;
        }

        // Nothing was reserved, so there is nothing to compensate: this is an immediate terminal failure.
        saga.setLastError("Inventory reservation failed: " + payload.reason());
        recordStep(saga, EventTypes.INVENTORY_RESERVE_FAILED, "FAILED", payload.reason());
        saga.setStatus(SagaStatus.COMPENSATED);
        saga.setCurrentStep(null);
        saga.setStepDeadline(null);
        saga.setRetryCount(0);
        publisher.publishTerminal(saga, EventTypes.SAGA_FAILED, "FAILED", saga.getLastError(), envelope);
    }

    @Transactional
    public void handleInventoryReleased(EventEnvelope envelope) {
        Payloads.InventoryReleased payload = EventJson.fromNode(envelope.payload(), Payloads.InventoryReleased.class);
        SagaInstance saga = findSaga(payload.sagaId(), payload.orderId());
        if (saga == null) return;
        if (saga.getStatus() != SagaStatus.COMPENSATING || !EventTypes.RELEASE_INVENTORY.equals(saga.getCurrentStep())) {
            log.info("Ignoring InventoryReleased for saga {} in status {} step {}", saga.getId(), saga.getStatus(), saga.getCurrentStep());
            return;
        }

        recordStep(saga, EventTypes.INVENTORY_RELEASED, "COMPLETED", "reservationId=" + payload.reservationId());
        saga.setStatus(SagaStatus.COMPENSATED);
        saga.setCurrentStep(null);
        saga.setStepDeadline(null);
        saga.setRetryCount(0);
        String reason = saga.getLastError() != null ? saga.getLastError() : "Saga compensated";
        publisher.publishTerminal(saga, EventTypes.SAGA_COMPENSATED, SagaStatus.COMPENSATED.name(), reason, envelope);
    }

    // ------------------------------------------------------------------------------------ Payment events

    @Transactional
    public void handlePaymentCaptured(EventEnvelope envelope) {
        Payloads.PaymentCaptured payload = EventJson.fromNode(envelope.payload(), Payloads.PaymentCaptured.class);
        SagaInstance saga = findSaga(payload.sagaId(), payload.orderId());
        if (saga == null) return;
        if (saga.getStatus() != SagaStatus.PAYING) {
            log.info("Ignoring PaymentCaptured for saga {} in status {}", saga.getId(), saga.getStatus());
            return;
        }

        saga.setPaymentId(payload.paymentId());
        saga.setStatus(SagaStatus.PAID);
        recordStep(saga, EventTypes.PAYMENT_CAPTURED, "COMPLETED", "paymentId=" + payload.paymentId());
        publisher.publishStepCompleted(saga, EventTypes.CAPTURE_PAYMENT, envelope);

        // Fulfillment is admin-driven: wait for HTTP create shipment (no auto CreateShipment, no deadline).
        saga.setCurrentStep(AWAIT_SHIPMENT);
        saga.setStepDeadline(null);
        saga.setRetryCount(0);
        recordStep(saga, AWAIT_SHIPMENT, "WAITING", "Awaiting admin shipment booking");
    }

    @Transactional
    public void handlePaymentFailed(EventEnvelope envelope) {
        Payloads.PaymentFailed payload = EventJson.fromNode(envelope.payload(), Payloads.PaymentFailed.class);
        SagaInstance saga = findSaga(payload.sagaId(), payload.orderId());
        if (saga == null) return;
        if (saga.getStatus() != SagaStatus.PAYING) {
            log.info("Ignoring PaymentFailed for saga {} in status {}", saga.getId(), saga.getStatus());
            return;
        }

        recordStep(saga, EventTypes.PAYMENT_FAILED, "FAILED", payload.reason());
        startReleaseOnly(saga, envelope, "Payment failed: " + payload.reason());
    }

    @Transactional
    public void handlePaymentRefunded(EventEnvelope envelope) {
        Payloads.PaymentRefunded payload = EventJson.fromNode(envelope.payload(), Payloads.PaymentRefunded.class);
        SagaInstance saga = findSaga(payload.sagaId(), payload.orderId());
        if (saga == null) return;
        if (saga.getStatus() != SagaStatus.COMPENSATING || !EventTypes.REFUND_PAYMENT.equals(saga.getCurrentStep())) {
            log.info("Ignoring PaymentRefunded for saga {} in status {} step {}", saga.getId(), saga.getStatus(), saga.getCurrentStep());
            return;
        }

        recordStep(saga, EventTypes.PAYMENT_REFUNDED, "COMPLETED", "paymentId=" + payload.paymentId());

        if (saga.getReservationId() == null) {
            // Nothing left to release; refund alone completes the compensation.
            saga.setStatus(SagaStatus.COMPENSATED);
            saga.setCurrentStep(null);
            saga.setStepDeadline(null);
            saga.setRetryCount(0);
            String reason = saga.getLastError() != null ? saga.getLastError() : "Saga compensated";
            publisher.publishTerminal(saga, EventTypes.SAGA_COMPENSATED, SagaStatus.COMPENSATED.name(), reason, envelope);
            return;
        }

        Payloads.ReleaseInventory command = new Payloads.ReleaseInventory(saga.getOrderId(), saga.sagaId(), saga.getReservationId());
        publisher.sendCommand(Topics.INVENTORY_COMMANDS, EventTypes.RELEASE_INVENTORY, saga, command, envelope);

        saga.setCurrentStep(EventTypes.RELEASE_INVENTORY);
        saga.setStepDeadline(Instant.now().plusSeconds(stepTimeoutSeconds));
        saga.setRetryCount(0);
        recordStep(saga, EventTypes.RELEASE_INVENTORY, "SENT", "Compensating: releasing inventory after refund");
    }

    // ----------------------------------------------------------------------------------- Shipping events

    @Transactional
    public void handleShipmentCreated(EventEnvelope envelope) {
        Payloads.ShipmentCreated payload = EventJson.fromNode(envelope.payload(), Payloads.ShipmentCreated.class);
        SagaInstance saga = findSaga(payload.sagaId(), payload.orderId());
        if (saga == null) return;
        if (saga.getStatus() != SagaStatus.PAID && saga.getStatus() != SagaStatus.SHIPPING) {
            log.info("Ignoring ShipmentCreated for saga {} in status {}", saga.getId(), saga.getStatus());
            return;
        }

        saga.setShipmentId(payload.shipmentId());
        recordStep(saga, EventTypes.SHIPMENT_CREATED, "COMPLETED",
                "shipmentId=" + payload.shipmentId() + " tracking=" + payload.trackingNumber());
        saga.setStatus(SagaStatus.COMPLETED);
        saga.setCurrentStep(null);
        saga.setStepDeadline(null);
        saga.setRetryCount(0);
        publisher.publishTerminal(saga, EventTypes.SAGA_COMPLETED, SagaStatus.COMPLETED.name(), "Shipment booked", envelope);
    }

    @Transactional
    public void handleShipmentFailed(EventEnvelope envelope) {
        Payloads.ShipmentFailed payload = EventJson.fromNode(envelope.payload(), Payloads.ShipmentFailed.class);
        SagaInstance saga = findSaga(payload.sagaId(), payload.orderId());
        if (saga == null) return;
        if (saga.getStatus() != SagaStatus.PAID && saga.getStatus() != SagaStatus.SHIPPING) {
            log.info("Ignoring ShipmentFailed for saga {} in status {}", saga.getId(), saga.getStatus());
            return;
        }

        recordStep(saga, EventTypes.SHIPMENT_FAILED, "FAILED", payload.reason());
        startRefundThenRelease(saga, envelope, "Shipment failed: " + payload.reason());
    }

    // ------------------------------------------------------------------------------------------ Timeouts

    @Transactional
    public void handleTimeout(Long sagaId) {
        SagaInstance saga = sagaInstanceRepository.findById(sagaId).orElse(null);
        if (saga == null) return;
        // Re-check under the transaction: another handler may have already resolved this saga
        // between the scheduler's read and this update.
        if (saga.getStepDeadline() == null || saga.getStepDeadline().isAfter(Instant.now())) {
            return;
        }

        saga.setRetryCount(saga.getRetryCount() + 1);
        String stuckStep = saga.getCurrentStep();
        recordStep(saga, stuckStep != null ? stuckStep : saga.getStatus().name(), "TIMEOUT",
                "Step deadline exceeded (attempt " + saga.getRetryCount() + ")");

        if (saga.getRetryCount() > maxRetries) {
            failNeedsAttention(saga, "Exceeded max retries (" + maxRetries + ") waiting for " + stuckStep, null);
            return;
        }

        switch (saga.getStatus()) {
            case RESERVING -> failNeedsAttention(saga, "Timed out waiting for inventory reservation response", null);
            case PAYING -> {
                saga.setLastError("Timed out waiting for payment capture response; releasing inventory");
                startReleaseOnly(saga, null, saga.getLastError());
            }
            case SHIPPING -> {
                // Legacy in-flight auto-create waits only; PAID awaits admin with null deadline.
                saga.setLastError("Timed out waiting for shipment creation response; refunding payment");
                startRefundThenRelease(saga, null, saga.getLastError());
            }
            case PAID -> log.info("Ignoring timeout for saga {} awaiting admin shipment (no deadline expected)", saga.getId());
            case COMPENSATING -> failNeedsAttention(saga,
                    "Timed out waiting for compensation step '" + stuckStep + "' to confirm", null);
            default -> log.warn("Timeout fired for saga {} in unexpected status {}", saga.getId(), saga.getStatus());
        }
    }

    // ------------------------------------------------------------------------------------------- Helpers

    private void startReleaseOnly(SagaInstance saga, EventEnvelope causation, String reason) {
        Payloads.ReleaseInventory command = new Payloads.ReleaseInventory(saga.getOrderId(), saga.sagaId(), saga.getReservationId());
        publisher.sendCommand(Topics.INVENTORY_COMMANDS, EventTypes.RELEASE_INVENTORY, saga, command, causation);

        saga.setStatus(SagaStatus.COMPENSATING);
        saga.setCurrentStep(EventTypes.RELEASE_INVENTORY);
        saga.setStepDeadline(Instant.now().plusSeconds(stepTimeoutSeconds));
        saga.setRetryCount(0);
        saga.setLastError(reason);
        recordStep(saga, EventTypes.RELEASE_INVENTORY, "SENT", reason);
    }

    private void startRefundThenRelease(SagaInstance saga, EventEnvelope causation, String reason) {
        Payloads.OrderCreated original = EventJson.read(saga.getPayloadJson(), Payloads.OrderCreated.class);
        Payloads.RefundPayment command = new Payloads.RefundPayment(
                saga.getOrderId(), saga.sagaId(), saga.getPaymentId(), original.totalAmount());
        publisher.sendCommand(Topics.PAYMENT_COMMANDS, EventTypes.REFUND_PAYMENT, saga, command, causation);

        saga.setStatus(SagaStatus.COMPENSATING);
        saga.setCurrentStep(EventTypes.REFUND_PAYMENT);
        saga.setStepDeadline(Instant.now().plusSeconds(stepTimeoutSeconds));
        saga.setRetryCount(0);
        saga.setLastError(reason);
        recordStep(saga, EventTypes.REFUND_PAYMENT, "SENT", reason);
    }

    private void failNeedsAttention(SagaInstance saga, String reason, EventEnvelope causation) {
        saga.setStatus(SagaStatus.FAILED_NEEDS_ATTENTION);
        saga.setCurrentStep(null);
        saga.setStepDeadline(null);
        saga.setLastError(reason);
        publisher.publishTerminal(saga, EventTypes.SAGA_FAILED, SagaStatus.FAILED_NEEDS_ATTENTION.name(), reason, causation);
    }

    private SagaInstance findSaga(String sagaId, String orderId) {
        if (sagaId != null) {
            try {
                SagaInstance saga = sagaInstanceRepository.findById(Long.valueOf(sagaId)).orElse(null);
                if (saga != null) return saga;
            } catch (NumberFormatException ex) {
                log.warn("Non-numeric sagaId '{}' received, falling back to orderId lookup", sagaId);
            }
        }
        if (orderId != null) {
            return sagaInstanceRepository.findByOrderId(orderId).orElse(null);
        }
        log.warn("Unable to resolve saga for sagaId={} orderId={}", sagaId, orderId);
        return null;
    }

    private void recordStep(SagaInstance saga, String stepName, String status, String detail) {
        sagaStepLogRepository.save(new SagaStepLog(saga.getId(), stepName, status, detail));
    }
}
