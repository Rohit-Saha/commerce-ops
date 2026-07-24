package com.commerceops.order.domain;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.FetchType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.OrderBy;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;

@Entity
@Table(name = "orders")
public class Order {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "customer_id", nullable = false)
    private String customerId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false, length = 32)
    private OrderStatus status;

    @Column(name = "total_amount", nullable = false, precision = 19, scale = 2)
    private BigDecimal totalAmount;

    @Column(nullable = false, length = 8)
    private String currency;

    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "ship_recipient_name", length = 128)
    private String shipRecipientName;

    @Column(name = "ship_line1", length = 256)
    private String shipLine1;

    @Column(name = "ship_line2", length = 256)
    private String shipLine2;

    @Column(name = "ship_city", length = 128)
    private String shipCity;

    @Column(name = "ship_state", length = 64)
    private String shipState;

    @Column(name = "ship_postal_code", length = 32)
    private String shipPostalCode;

    @Column(name = "ship_country", length = 64)
    private String shipCountry;

    @Column(name = "ship_source_address_id", length = 36)
    private String shipSourceAddressId;

    @Column(name = "fulfillment_started", nullable = false)
    private boolean fulfillmentStarted = false;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @OneToMany(mappedBy = "order", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @OrderBy("id ASC")
    private List<OrderLine> lines = new ArrayList<>();

    public String getId() { return id; }
    public void setId(String id) { this.id = id; }
    public String getCustomerId() { return customerId; }
    public void setCustomerId(String customerId) { this.customerId = customerId; }
    public OrderStatus getStatus() { return status; }
    public void setStatus(OrderStatus status) { this.status = status; }
    public BigDecimal getTotalAmount() { return totalAmount; }
    public void setTotalAmount(BigDecimal totalAmount) { this.totalAmount = totalAmount; }
    public String getCurrency() { return currency; }
    public void setCurrency(String currency) { this.currency = currency; }
    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getShipRecipientName() { return shipRecipientName; }
    public void setShipRecipientName(String shipRecipientName) { this.shipRecipientName = shipRecipientName; }
    public String getShipLine1() { return shipLine1; }
    public void setShipLine1(String shipLine1) { this.shipLine1 = shipLine1; }
    public String getShipLine2() { return shipLine2; }
    public void setShipLine2(String shipLine2) { this.shipLine2 = shipLine2; }
    public String getShipCity() { return shipCity; }
    public void setShipCity(String shipCity) { this.shipCity = shipCity; }
    public String getShipState() { return shipState; }
    public void setShipState(String shipState) { this.shipState = shipState; }
    public String getShipPostalCode() { return shipPostalCode; }
    public void setShipPostalCode(String shipPostalCode) { this.shipPostalCode = shipPostalCode; }
    public String getShipCountry() { return shipCountry; }
    public void setShipCountry(String shipCountry) { this.shipCountry = shipCountry; }
    public String getShipSourceAddressId() { return shipSourceAddressId; }
    public void setShipSourceAddressId(String shipSourceAddressId) { this.shipSourceAddressId = shipSourceAddressId; }
    public boolean isFulfillmentStarted() { return fulfillmentStarted; }
    public void setFulfillmentStarted(boolean fulfillmentStarted) { this.fulfillmentStarted = fulfillmentStarted; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public List<OrderLine> getLines() { return lines; }
    public void setLines(List<OrderLine> lines) { this.lines = lines; }

    public void addLine(OrderLine line) {
        line.setOrder(this);
        this.lines.add(line);
    }
}
