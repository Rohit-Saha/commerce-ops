package com.commerceops.shipping.service;

import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import com.commerceops.common.events.EventTypes;
import com.commerceops.common.events.Payloads;
import com.commerceops.common.events.Topics;
import com.commerceops.common.kafka.OutboxService;
import com.commerceops.common.web.BusinessException;
import com.commerceops.shipping.carrier.ShippingProvider;
import com.commerceops.shipping.config.ShippingProperties;
import com.commerceops.shipping.domain.Shipment;
import com.commerceops.shipping.domain.ShipmentEvent;
import com.commerceops.shipping.domain.ShipmentStatus;
import com.commerceops.shipping.repository.ShipmentEventRepository;
import com.commerceops.shipping.repository.ShipmentRepository;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.EnumSet;
import java.util.List;
import java.util.Locale;
import java.util.Optional;
import java.util.UUID;

@Service
public class ShippingService {

    private static final Logger log = LoggerFactory.getLogger(ShippingService.class);

    private static final List<ShipmentStatus> ADVANCE_ORDER = List.of(
            ShipmentStatus.CREATED,
            ShipmentStatus.PICKED_UP,
            ShipmentStatus.IN_TRANSIT,
            ShipmentStatus.OUT_FOR_DELIVERY,
            ShipmentStatus.DELIVERED
    );

    private final ShipmentRepository shipmentRepository;
    private final ShipmentEventRepository shipmentEventRepository;
    private final OutboxService outboxService;
    private final ShippingProvider shippingProvider;
    private final ShippingProperties properties;
    private final OrderSnapshotClient orderSnapshotClient;
    private final SagaLookupClient sagaLookupClient;

    public ShippingService(
            ShipmentRepository shipmentRepository,
            ShipmentEventRepository shipmentEventRepository,
            OutboxService outboxService,
            ShippingProvider shippingProvider,
            ShippingProperties properties,
            OrderSnapshotClient orderSnapshotClient,
            SagaLookupClient sagaLookupClient) {
        this.shipmentRepository = shipmentRepository;
        this.shipmentEventRepository = shipmentEventRepository;
        this.outboxService = outboxService;
        this.shippingProvider = shippingProvider;
        this.properties = properties;
        this.orderSnapshotClient = orderSnapshotClient;
        this.sagaLookupClient = sagaLookupClient;
    }

    @Transactional
    public Shipment createShipmentForOrder(String orderId) {
        OrderSnapshotClient.OrderSnapshot order = orderSnapshotClient.fetch(orderId);
        if (!"PAID".equalsIgnoreCase(order.status())) {
            throw BusinessException.conflict(
                    "Shipment can only be created for PAID orders (current status: "
                            + order.status() + ").");
        }
        SagaLookupClient.SagaRef saga = sagaLookupClient.fetchByOrder(orderId);
        if (!"PAID".equalsIgnoreCase(saga.status()) && !"SHIPPING".equalsIgnoreCase(saga.status())) {
            throw BusinessException.conflict(
                    "Saga is not awaiting shipment (current status: " + saga.status() + ").");
        }
        Payloads.CreateShipment cmd = new Payloads.CreateShipment(
                order.orderId(),
                saga.sagaId(),
                order.customerId(),
                order.lines(),
                order.shippingAddress());
        return createShipment(cmd, orderId, null);
    }

    @Transactional
    public Shipment createShipment(Payloads.CreateShipment cmd, String correlationId, String causationId) {
        Optional<Shipment> existing = shipmentRepository.findFirstByOrderIdAndStatusNotInOrderByCreatedAtDesc(
                cmd.orderId(), EnumSet.of(ShipmentStatus.FAILED, ShipmentStatus.CANCELLED));
        if (existing.isPresent()) {
            log.info("Reusing existing shipment {} for order {}", existing.get().getId(), cmd.orderId());
            return existing.get();
        }

        Payloads.ShippingAddress address = cmd.shippingAddress();
        if (address == null || isBlank(address.recipientName()) || isBlank(address.line1())) {
            address = orderSnapshotClient.fetchAddress(cmd.orderId());
        }

        Shipment shipment = new Shipment();
        shipment.setOrderId(cmd.orderId());
        shipment.setSagaId(cmd.sagaId());
        shipment.setWeightKg(BigDecimal.valueOf(properties.getDefaultWeightKg()));
        applyAddress(shipment, address);

        ShippingProvider.BookingResult booked = shippingProvider.book(new ShippingProvider.BookingRequest(
                cmd.orderId(),
                cmd.customerId(),
                cmd.lines(),
                address,
                shipment.getWeightKg()));

        Instant now = Instant.now();
        shipment.setStatusUpdatedAt(now);
        if (!booked.success()) {
            shipment.setStatus(ShipmentStatus.FAILED);
            shipment.setFailureReason(truncate(booked.failureReason(), 255));
            shipment.setCarrier(booked.carrier());
            shipment = shipmentRepository.save(shipment);
            appendEvent(shipment, ShipmentStatus.FAILED, null, booked.failureReason(), now);
            publishShipmentFailed(shipment, correlationId, causationId, booked.failureReason());
            log.warn("Shipment creation failed for orderId={} reason={}", shipment.getOrderId(), booked.failureReason());
            return shipment;
        }

        shipment.setStatus(ShipmentStatus.CREATED);
        shipment.setCarrier(booked.carrier());
        shipment.setCarrierOrderId(booked.carrierOrderId());
        shipment.setTrackingNumber(booked.trackingNumber());
        shipment.setLabelUrl(booked.labelUrl());
        shipment = shipmentRepository.save(shipment);
        String bookedMsg = "Label booked"
                + (booked.trackingNumber() != null ? " · tracking " + booked.trackingNumber() : "")
                + (booked.carrier() != null ? " via " + booked.carrier() : "");
        appendEvent(shipment, ShipmentStatus.CREATED, null, bookedMsg, now);
        publishShipmentCreated(shipment, correlationId, causationId);
        log.info("Shipment created for orderId={} shipmentId={} tracking={} carrier={}",
                shipment.getOrderId(), shipment.getId(), shipment.getTrackingNumber(), shipment.getCarrier());
        return shipment;
    }

    @Transactional(readOnly = true)
    public List<Shipment> listAll() {
        return shipmentRepository.findAll();
    }

    @Transactional(readOnly = true)
    public List<Shipment> listByOrder(String orderId) {
        return shipmentRepository.findByOrderIdOrderByCreatedAtDesc(orderId);
    }

    @Transactional(readOnly = true)
    public Shipment get(Long id) {
        return shipmentRepository.findById(id)
                .orElseThrow(() -> BusinessException.notFound("We couldn’t find that shipment."));
    }

    @Transactional(readOnly = true)
    public List<ShipmentEvent> events(Long shipmentId) {
        get(shipmentId);
        return shipmentEventRepository.findByShipmentIdOrderByOccurredAtAsc(shipmentId);
    }

    @Transactional
    public Shipment advance(Long id) {
        Shipment shipment = get(id);
        if (shipment.getStatus() == ShipmentStatus.FAILED
                || shipment.getStatus() == ShipmentStatus.CANCELLED
                || shipment.getStatus() == ShipmentStatus.DELIVERED
                || shipment.getStatus() == ShipmentStatus.RTO) {
            throw BusinessException.conflict("Shipment cannot be advanced from status " + shipment.getStatus());
        }
        int idx = ADVANCE_ORDER.indexOf(shipment.getStatus());
        if (idx < 0 || idx >= ADVANCE_ORDER.size() - 1) {
            throw BusinessException.conflict("No next status for " + shipment.getStatus());
        }
        ShipmentStatus next = ADVANCE_ORDER.get(idx + 1);
        return applyStatus(shipment, next, "advance", "Advanced to " + next.name(), Instant.now(), true);
    }

    @Transactional
    public Shipment applyCarrierStatus(
            String trackingOrCarrierOrderId,
            String rawCode,
            String message,
            Instant occurredAt) {
        Shipment shipment = shipmentRepository.findByTrackingNumber(trackingOrCarrierOrderId)
                .or(() -> shipmentRepository.findByCarrierOrderId(trackingOrCarrierOrderId))
                .orElse(null);
        if (shipment == null) {
            log.info("Ignoring carrier status for unknown shipment ref {}", trackingOrCarrierOrderId);
            return null;
        }
        ShipmentStatus mapped = mapCarrierStatus(rawCode, message);
        if (mapped == null || mapped == shipment.getStatus()) {
            return shipment;
        }
        return applyStatus(shipment, mapped, rawCode, message, occurredAt != null ? occurredAt : Instant.now(), true);
    }

    private Shipment applyStatus(
            Shipment shipment,
            ShipmentStatus status,
            String rawCode,
            String message,
            Instant when,
            boolean publish) {
        shipment.setStatus(status);
        shipment.setStatusReason(truncate(message, 255));
        shipment.setStatusUpdatedAt(when);
        shipment = shipmentRepository.save(shipment);
        appendEvent(shipment, status, rawCode, message, when);
        if (publish) {
            publishStatusUpdated(shipment, message);
        }
        return shipment;
    }

    static ShipmentStatus mapCarrierStatus(String rawCode, String message) {
        String hay = ((rawCode != null ? rawCode : "") + " " + (message != null ? message : ""))
                .toLowerCase(Locale.ROOT);
        if (hay.contains("rto") || hay.contains("return")) {
            return ShipmentStatus.RTO;
        }
        if (hay.contains("cancel")) {
            return ShipmentStatus.CANCELLED;
        }
        if (hay.contains("deliver")) {
            return ShipmentStatus.DELIVERED;
        }
        if (hay.contains("out for delivery") || hay.contains("ofd")) {
            return ShipmentStatus.OUT_FOR_DELIVERY;
        }
        if (hay.contains("transit") || hay.contains("in transit") || hay.contains("dispatched")) {
            return ShipmentStatus.IN_TRANSIT;
        }
        if (hay.contains("pick") || hay.contains("picked") || hay.contains("manifest")) {
            return ShipmentStatus.PICKED_UP;
        }
        if (hay.contains("shipped") || hay.contains("awb")) {
            return ShipmentStatus.IN_TRANSIT;
        }
        return null;
    }

    private void applyAddress(Shipment shipment, Payloads.ShippingAddress address) {
        if (address == null) {
            return;
        }
        shipment.setRecipientName(address.recipientName());
        shipment.setAddressLine1(address.line1());
        shipment.setAddressLine2(address.line2());
        shipment.setCity(address.city());
        shipment.setState(address.state());
        shipment.setPostalCode(address.postalCode());
        shipment.setCountry(address.country());
    }

    private void appendEvent(
            Shipment shipment, ShipmentStatus status, String rawCode, String message, Instant when) {
        ShipmentEvent event = new ShipmentEvent();
        event.setShipment(shipment);
        event.setStatus(status);
        event.setRawCode(rawCode);
        event.setMessage(truncate(message, 512));
        event.setOccurredAt(when);
        shipmentEventRepository.save(event);
    }

    private void publishShipmentCreated(Shipment shipment, String correlationId, String causationId) {
        Payloads.ShipmentCreated payload = new Payloads.ShipmentCreated(
                shipment.getOrderId(),
                shipment.getSagaId(),
                String.valueOf(shipment.getId()),
                shipment.getTrackingNumber(),
                shipment.getCarrier(),
                shipment.getLabelUrl());
        enqueue(EventTypes.SHIPMENT_CREATED, shipment, correlationId, causationId, payload);
    }

    private void publishShipmentFailed(Shipment shipment, String correlationId, String causationId, String reason) {
        Payloads.ShipmentFailed payload = new Payloads.ShipmentFailed(
                shipment.getOrderId(), shipment.getSagaId(), reason);
        enqueue(EventTypes.SHIPMENT_FAILED, shipment, correlationId, causationId, payload);
    }

    private void publishStatusUpdated(Shipment shipment, String message) {
        Payloads.ShipmentStatusUpdated payload = new Payloads.ShipmentStatusUpdated(
                shipment.getOrderId(),
                shipment.getSagaId(),
                String.valueOf(shipment.getId()),
                shipment.getStatus().name(),
                shipment.getTrackingNumber(),
                message);
        enqueue(EventTypes.SHIPMENT_STATUS_UPDATED, shipment, shipment.getOrderId(), null, payload);
    }

    private void enqueue(String eventType, Shipment shipment, String correlationId, String causationId, Object payload) {
        EventEnvelope envelope = EventEnvelope.of(
                eventType,
                shipment.getOrderId(),
                correlationId,
                causationId,
                shipment.getSagaId(),
                UUID.randomUUID().toString(),
                EventJson.toNode(payload));
        outboxService.enqueue(Topics.SHIPPING_EVENTS, envelope);
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String truncate(String value, int max) {
        if (value == null) {
            return null;
        }
        return value.length() <= max ? value : value.substring(0, max);
    }
}
