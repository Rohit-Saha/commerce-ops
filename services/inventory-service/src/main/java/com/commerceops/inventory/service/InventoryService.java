package com.commerceops.inventory.service;

import com.commerceops.common.events.EventEnvelope;
import com.commerceops.common.events.EventJson;
import com.commerceops.common.events.EventTypes;
import com.commerceops.common.events.Payloads;
import com.commerceops.common.events.Topics;
import com.commerceops.common.kafka.OutboxService;
import com.commerceops.inventory.domain.Reservation;
import com.commerceops.inventory.domain.ReservationLine;
import com.commerceops.inventory.domain.ReservationStatus;
import com.commerceops.inventory.domain.StockItem;
import com.commerceops.inventory.repository.ReservationRepository;
import com.commerceops.inventory.repository.StockItemRepository;
import com.commerceops.inventory.service.exception.InsufficientStockException;
import com.commerceops.inventory.service.exception.ReservationNotFoundException;
import com.commerceops.inventory.service.exception.StockItemConflictException;
import com.commerceops.inventory.service.exception.StockItemInUseException;
import com.commerceops.inventory.service.exception.StockItemNotFoundException;
import com.commerceops.inventory.web.dto.CreateStockItemRequest;
import com.commerceops.inventory.web.dto.StockItemResponse;
import com.commerceops.inventory.web.dto.UpdateStockItemRequest;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Service
public class InventoryService {

    private static final Logger log = LoggerFactory.getLogger(InventoryService.class);

    private final StockItemRepository stockItemRepository;
    private final ReservationRepository reservationRepository;
    private final InventoryCacheService cacheService;
    private final OutboxService outboxService;

    public InventoryService(StockItemRepository stockItemRepository,
                             ReservationRepository reservationRepository,
                             InventoryCacheService cacheService,
                             OutboxService outboxService) {
        this.stockItemRepository = stockItemRepository;
        this.reservationRepository = reservationRepository;
        this.cacheService = cacheService;
        this.outboxService = outboxService;
    }

    @Transactional(readOnly = true)
    public List<StockItemResponse> listStock() {
        return stockItemRepository.findByDeletedAtIsNull().stream().map(this::toResponseWithCache).toList();
    }

    @Transactional(readOnly = true)
    public StockItemResponse getStock(String sku) {
        StockItem item = requireActive(sku);
        return toResponseWithCache(item);
    }

    @Transactional
    public StockItemResponse createStock(CreateStockItemRequest request) {
        String sku = request.sku().trim();
        if (stockItemRepository.existsById(sku)) {
            StockItem existing = stockItemRepository.findById(sku).orElseThrow();
            if (existing.isDeleted()) {
                throw new StockItemConflictException(
                        "SKU already exists but is soft-deleted: " + sku + " (recreate/undelete not supported)");
            }
            throw new StockItemConflictException("SKU already exists: " + sku);
        }

        int availableQty = request.availableQty() == null ? 0 : request.availableQty();
        BigDecimal unitPrice = normalizePrice(request.unitPrice());
        StockItem item = new StockItem(sku, request.name().trim(), availableQty, 0, unitPrice);
        stockItemRepository.save(item);
        cacheService.writeThrough(item);
        publishStockItemChanged(item);
        log.info("Created stock item sku={} name={} available={} unitPrice={}",
                sku, item.getName(), availableQty, unitPrice);
        return toResponse(item, item.getAvailableQty());
    }

    @Transactional
    public StockItemResponse updateStock(String sku, UpdateStockItemRequest request) {
        StockItem item = requireActive(sku);
        item.setName(request.name().trim());
        item.setUnitPrice(normalizePrice(request.unitPrice()));
        stockItemRepository.save(item);
        cacheService.writeThrough(item);
        publishStockItemChanged(item);
        log.info("Updated stock item sku={} name={} unitPrice={}", sku, item.getName(), item.getUnitPrice());
        return toResponseWithCache(item);
    }

    @Transactional
    public void softDeleteStock(String sku) {
        StockItem item = requireActive(sku);
        if (item.getReservedQty() > 0) {
            throw new StockItemInUseException(sku, item.getReservedQty());
        }
        item.setDeletedAt(Instant.now());
        stockItemRepository.save(item);
        cacheService.evict(sku);
        publishStockItemChanged(item);
        log.info("Soft-deleted stock item sku={}", sku);
    }

    /**
     * Reserves inventory for every line of an order. Validates all lines first so a
     * shortage on any single SKU leaves stock untouched (all-or-nothing reservation).
     */
    @Transactional
    public String reserve(String orderId, String sagaId, List<Payloads.OrderLine> lines) {
        List<StockItem> locked = new java.util.ArrayList<>();
        for (Payloads.OrderLine line : lines) {
            StockItem item = stockItemRepository.findActiveWithLockBySku(line.sku())
                    .orElseThrow(() -> new InsufficientStockException(line.sku(), "Unknown SKU: " + line.sku()));
            if (item.getAvailableQty() < line.quantity()) {
                throw new InsufficientStockException(line.sku(),
                        "Insufficient stock for " + line.sku() + ": requested=" + line.quantity()
                                + " available=" + item.getAvailableQty());
            }
            locked.add(item);
        }

        Instant now = Instant.now();
        Reservation reservation = new Reservation();
        reservation.setId(UUID.randomUUID().toString());
        reservation.setOrderId(orderId);
        reservation.setSagaId(sagaId);
        reservation.setStatus(ReservationStatus.CONFIRMED);
        reservation.setCreatedAt(now);
        reservation.setUpdatedAt(now);

        for (int i = 0; i < lines.size(); i++) {
            Payloads.OrderLine line = lines.get(i);
            StockItem item = locked.get(i);
            item.setAvailableQty(item.getAvailableQty() - line.quantity());
            item.setReservedQty(item.getReservedQty() + line.quantity());
            stockItemRepository.save(item);
            cacheService.writeThrough(item);
            publishStockItemChanged(item);

            ReservationLine reservationLine = new ReservationLine();
            reservationLine.setSku(line.sku());
            reservationLine.setQuantity(line.quantity());
            reservation.addLine(reservationLine);
        }

        reservationRepository.save(reservation);
        log.info("Reserved inventory for order={} reservation={} lines={}", orderId, reservation.getId(), lines.size());
        return reservation.getId();
    }

    /**
     * Releases a previously confirmed reservation, returning quantities back to available
     * stock. Looked up by reservation id when provided, otherwise falls back to the most
     * recent confirmed reservation for the order. Idempotent: releasing an already released
     * reservation is a no-op.
     */
    @Transactional
    public String release(String orderId, String reservationId) {
        Reservation reservation = findReservation(orderId, reservationId);

        if (reservation.getStatus() == ReservationStatus.RELEASED) {
            log.debug("Reservation {} already released, skipping", reservation.getId());
            return reservation.getId();
        }

        for (ReservationLine line : reservation.getLines()) {
            StockItem item = stockItemRepository.findWithLockBySku(line.getSku())
                    .orElseThrow(() -> new StockItemNotFoundException(line.getSku()));
            item.setAvailableQty(item.getAvailableQty() + line.getQuantity());
            item.setReservedQty(Math.max(0, item.getReservedQty() - line.getQuantity()));
            stockItemRepository.save(item);
            if (!item.isDeleted()) {
                cacheService.writeThrough(item);
            }
            publishStockItemChanged(item);
        }

        reservation.setStatus(ReservationStatus.RELEASED);
        reservation.setUpdatedAt(Instant.now());
        reservationRepository.save(reservation);
        log.info("Released inventory for order={} reservation={}", orderId, reservation.getId());
        return reservation.getId();
    }

    @Transactional
    public StockItemResponse restock(String sku, int qty) {
        if (qty <= 0) {
            throw new IllegalArgumentException("qty must be positive");
        }
        StockItem item = stockItemRepository.findActiveWithLockBySku(sku)
                .orElseThrow(() -> new StockItemNotFoundException(sku));
        item.setAvailableQty(item.getAvailableQty() + qty);
        stockItemRepository.save(item);
        cacheService.writeThrough(item);
        publishStockItemChanged(item);
        log.info("Restocked sku={} by qty={}, new available={}", sku, qty, item.getAvailableQty());
        return toResponse(item, item.getAvailableQty());
    }

    private void publishStockItemChanged(StockItem item) {
        Payloads.StockItemChanged payload = new Payloads.StockItemChanged(
                item.getSku(),
                item.getName(),
                item.getUnitPrice(),
                item.getAvailableQty(),
                item.isDeleted());
        EventEnvelope envelope = EventEnvelope.of(
                EventTypes.STOCK_ITEM_CHANGED,
                item.getSku(),
                item.getSku(),
                null,
                null,
                null,
                EventJson.toNode(payload));
        outboxService.enqueue(Topics.INVENTORY_EVENTS, envelope);
    }

    private StockItem requireActive(String sku) {
        return stockItemRepository.findBySkuAndDeletedAtIsNull(sku)
                .orElseThrow(() -> new StockItemNotFoundException(sku));
    }

    private static BigDecimal normalizePrice(BigDecimal unitPrice) {
        return unitPrice.setScale(2, RoundingMode.HALF_UP);
    }

    private Reservation findReservation(String orderId, String reservationId) {
        if (reservationId != null && !reservationId.isBlank()) {
            return reservationRepository.findById(reservationId)
                    .orElseThrow(() -> new ReservationNotFoundException(reservationId));
        }
        return reservationRepository
                .findFirstByOrderIdAndStatusOrderByCreatedAtDesc(orderId, ReservationStatus.CONFIRMED)
                .orElseThrow(() -> new ReservationNotFoundException("order:" + orderId));
    }

    private StockItemResponse toResponseWithCache(StockItem item) {
        int availableQty = cacheService.getCachedAvailableQty(item.getSku())
                .orElseGet(() -> {
                    cacheService.writeThrough(item);
                    return item.getAvailableQty();
                });
        return toResponse(item, availableQty);
    }

    private StockItemResponse toResponse(StockItem item, int availableQty) {
        return new StockItemResponse(
                item.getSku(),
                item.getName(),
                availableQty,
                item.getReservedQty(),
                item.getUnitPrice());
    }
}
