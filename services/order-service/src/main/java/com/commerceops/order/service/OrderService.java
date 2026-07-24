package com.commerceops.order.service;

import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import com.commerceops.common.events.EventTypes;
import com.commerceops.common.events.Payloads;
import com.commerceops.common.events.Topics;
import com.commerceops.common.kafka.OutboxService;
import com.commerceops.order.domain.ClientIdempotency;
import com.commerceops.order.domain.Order;
import com.commerceops.order.domain.OrderLine;
import com.commerceops.order.domain.OrderStatus;
import com.commerceops.order.repository.ClientIdempotencyRepository;
import com.commerceops.order.repository.OrderRepository;
import com.commerceops.order.service.exception.InvalidOrderStateException;
import com.commerceops.order.service.exception.OrderNotFoundException;
import com.commerceops.order.web.dto.CreateOrderLineRequest;
import com.commerceops.order.web.dto.CreateOrderRequest;
import com.commerceops.order.web.dto.OrderLineResponse;
import com.commerceops.order.web.dto.OrderResponse;
import com.commerceops.order.web.dto.ShippingAddressDto;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class OrderService {

    private static final Logger log = LoggerFactory.getLogger(OrderService.class);

    private final OrderRepository orderRepository;
    private final ClientIdempotencyRepository clientIdempotencyRepository;
    private final OutboxService outboxService;

    public OrderService(OrderRepository orderRepository,
                         ClientIdempotencyRepository clientIdempotencyRepository,
                         OutboxService outboxService) {
        this.orderRepository = orderRepository;
        this.clientIdempotencyRepository = clientIdempotencyRepository;
        this.outboxService = outboxService;
    }

    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey) {
        return createOrder(request, idempotencyKey, true);
    }

    /**
     * @param startFulfillment when false, persists PENDING order without publishing OrderCreated
     *                         (gateway authorizes payment first, then calls {@link #startFulfillment}).
     */
    @Transactional
    public OrderResponse createOrder(CreateOrderRequest request, String idempotencyKey, boolean startFulfillment) {
        String normalizedKey = normalizeKey(idempotencyKey);

        if (normalizedKey != null) {
            var existing = clientIdempotencyRepository.findById(normalizedKey);
            if (existing.isPresent()) {
                Order order = orderRepository.findById(existing.get().getOrderId())
                        .orElseThrow(() -> new OrderNotFoundException(existing.get().getOrderId()));
                log.info("Idempotent replay for key={}, returning existing order={}", normalizedKey, order.getId());
                return toResponse(order);
            }
        }

        Instant now = Instant.now();
        Order order = new Order();
        order.setId(UUID.randomUUID().toString());
        order.setCustomerId(request.customerId());
        order.setStatus(OrderStatus.PENDING);
        order.setCurrency(request.currency());
        order.setIdempotencyKey(normalizedKey);
        order.setFulfillmentStarted(false);
        order.setCreatedAt(now);
        order.setUpdatedAt(now);
        applyShippingAddress(order, request.shippingAddress());

        BigDecimal total = BigDecimal.ZERO;
        for (CreateOrderLineRequest lineRequest : request.lines()) {
            OrderLine line = new OrderLine();
            line.setSku(lineRequest.sku());
            line.setQuantity(lineRequest.quantity());
            line.setUnitPrice(lineRequest.unitPrice());
            order.addLine(line);
            total = total.add(lineRequest.unitPrice().multiply(BigDecimal.valueOf(lineRequest.quantity())));
        }
        order.setTotalAmount(total);

        try {
            order = orderRepository.save(order);
        } catch (DataIntegrityViolationException ex) {
            if (normalizedKey != null) {
                var raced = orderRepository.findByIdempotencyKey(normalizedKey);
                if (raced.isPresent()) {
                    log.info("Race on idempotency key={}, returning concurrently created order", normalizedKey);
                    return toResponse(raced.get());
                }
            }
            throw ex;
        }

        if (normalizedKey != null) {
            try {
                clientIdempotencyRepository.save(new ClientIdempotency(normalizedKey, order.getId(), now));
            } catch (DataIntegrityViolationException ex) {
                log.debug("client_idempotency row for key={} already recorded concurrently", normalizedKey);
            }
        }

        if (startFulfillment) {
            order.setFulfillmentStarted(true);
            order = orderRepository.save(order);
            publishOrderCreated(order);
        }

        return toResponse(order);
    }

    @Transactional
    public OrderResponse startFulfillment(String orderId) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.isFulfillmentStarted()) {
            log.info("Fulfillment already started for order={}, skipping publish", orderId);
            return toResponse(order);
        }
        if (order.getStatus() != OrderStatus.PENDING) {
            throw new InvalidOrderStateException(
                    "Order " + orderId + " cannot start fulfillment from state " + order.getStatus());
        }
        order.setFulfillmentStarted(true);
        order.setUpdatedAt(Instant.now());
        order = orderRepository.save(order);
        publishOrderCreated(order);
        return toResponse(order);
    }

    @Transactional
    public OrderResponse abandonPending(String orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
        if (order.isFulfillmentStarted()) {
            throw new InvalidOrderStateException(
                    "Order " + orderId + " already started fulfillment; use cancel instead");
        }
        if (order.getStatus().isTerminal()) {
            return toResponse(order);
        }
        log.info("Abandoning pending order {} before saga: {}", orderId, reason);
        order.setStatus(OrderStatus.FAILED);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);
        return toResponse(order);
    }

    @Transactional(readOnly = true)
    public OrderResponse getOrder(String orderId) {
        return orderRepository.findById(orderId)
                .map(this::toResponse)
                .orElseThrow(() -> new OrderNotFoundException(orderId));
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrders() {
        return orderRepository.findAll().stream().map(this::toResponse).toList();
    }

    @Transactional(readOnly = true)
    public List<OrderResponse> listOrdersByCustomer(String customerId) {
        return orderRepository.findByCustomerIdOrderByCreatedAtDesc(customerId).stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional
    public OrderResponse requestCancel(String orderId, String reason) {
        Order order = orderRepository.findById(orderId)
                .orElseThrow(() -> new OrderNotFoundException(orderId));

        if (!order.getStatus().isCancellablePreShip()) {
            throw new InvalidOrderStateException(
                    "Order " + orderId + " cannot be cancelled from status " + order.getStatus()
                            + ". Cancel is only available before a shipment is created.");
        }

        Payloads.OrderCancelRequested payload = new Payloads.OrderCancelRequested(
                orderId, reason != null && !reason.isBlank() ? reason : "Customer requested cancellation");
        EventEnvelope envelope = EventEnvelope.of(
                EventTypes.ORDER_CANCEL_REQUESTED,
                orderId,
                orderId,
                null,
                null,
                null,
                EventJson.toNode(payload));
        outboxService.enqueue(Topics.ORDER_EVENTS, envelope);

        return toResponse(order);
    }

    @Transactional
    public void updateStatus(String orderId, OrderStatus newStatus, String reason) {
        Order order = orderRepository.findById(orderId).orElse(null);
        if (order == null) {
            log.warn("Received status update to {} for unknown order {}", newStatus, orderId);
            return;
        }
        if (order.getStatus().isTerminal()) {
            log.debug("Ignoring status update to {} for order {} already terminal at {}",
                    newStatus, orderId, order.getStatus());
            return;
        }
        if (order.getStatus() == newStatus) {
            return;
        }

        log.info("Order {} status transition {} -> {} ({})", orderId, order.getStatus(), newStatus, reason);
        order.setStatus(newStatus);
        order.setUpdatedAt(Instant.now());
        orderRepository.save(order);

        Payloads.OrderStatusChanged payload = new Payloads.OrderStatusChanged(orderId, newStatus.name(), reason);
        EventEnvelope envelope = EventEnvelope.of(
                EventTypes.ORDER_STATUS_CHANGED,
                orderId,
                orderId,
                null,
                null,
                null,
                EventJson.toNode(payload));
        outboxService.enqueue(Topics.ORDER_EVENTS, envelope);
    }

    private void publishOrderCreated(Order order) {
        List<Payloads.OrderLine> lines = order.getLines().stream()
                .map(l -> new Payloads.OrderLine(l.getSku(), l.getQuantity(), l.getUnitPrice()))
                .toList();

        ShippingAddressDto ship = toShippingAddress(order);
        Payloads.ShippingAddress shippingAddress = ship == null ? null : new Payloads.ShippingAddress(
                ship.recipientName(),
                ship.line1(),
                ship.line2(),
                ship.city(),
                ship.state(),
                ship.postalCode(),
                ship.country());
        Payloads.OrderCreated payload = new Payloads.OrderCreated(
                order.getId(),
                order.getCustomerId(),
                lines,
                order.getTotalAmount(),
                order.getCurrency(),
                shippingAddress);

        EventEnvelope envelope = EventEnvelope.of(
                EventTypes.ORDER_CREATED,
                order.getId(),
                order.getId(),
                null,
                null,
                order.getIdempotencyKey(),
                EventJson.toNode(payload));

        outboxService.enqueue(Topics.ORDER_EVENTS, envelope);
    }

    private OrderResponse toResponse(Order order) {
        List<OrderLineResponse> lines = order.getLines().stream()
                .map(l -> new OrderLineResponse(l.getSku(), l.getQuantity(), l.getUnitPrice()))
                .toList();
        return new OrderResponse(
                order.getId(),
                order.getCustomerId(),
                order.getStatus().name(),
                order.getTotalAmount(),
                order.getCurrency(),
                order.getIdempotencyKey(),
                lines,
                toShippingAddress(order),
                order.getCreatedAt(),
                order.getUpdatedAt());
    }

    private void applyShippingAddress(Order order, ShippingAddressDto address) {
        if (address == null) {
            return;
        }
        order.setShipRecipientName(trimToNull(address.recipientName()));
        order.setShipLine1(trimToNull(address.line1()));
        order.setShipLine2(trimToNull(address.line2()));
        order.setShipCity(trimToNull(address.city()));
        order.setShipState(trimToNull(address.state()));
        order.setShipPostalCode(trimToNull(address.postalCode()));
        order.setShipCountry(trimToNull(address.country()));
        order.setShipSourceAddressId(trimToNull(address.sourceAddressId()));
    }

    private static ShippingAddressDto toShippingAddress(Order order) {
        if (order.getShipLine1() == null && order.getShipRecipientName() == null) {
            return null;
        }
        return new ShippingAddressDto(
                order.getShipRecipientName(),
                order.getShipLine1(),
                order.getShipLine2(),
                order.getShipCity(),
                order.getShipState(),
                order.getShipPostalCode(),
                order.getShipCountry(),
                order.getShipSourceAddressId());
    }

    private static String trimToNull(String value) {
        if (value == null || value.isBlank()) {
            return null;
        }
        return value.trim();
    }

    private String normalizeKey(String idempotencyKey) {
        if (idempotencyKey == null || idempotencyKey.isBlank()) {
            return null;
        }
        return idempotencyKey.trim();
    }
}
