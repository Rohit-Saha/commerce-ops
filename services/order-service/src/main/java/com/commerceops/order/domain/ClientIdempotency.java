package com.commerceops.order.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.Instant;

@Entity
@Table(name = "client_idempotency")
public class ClientIdempotency {

    @Id
    @Column(name = "idempotency_key", length = 128)
    private String idempotencyKey;

    @Column(name = "order_id", nullable = false, length = 36)
    private String orderId;

    @Column(name = "created_at", nullable = false)
    private Instant createdAt;

    public ClientIdempotency() {}

    public ClientIdempotency(String idempotencyKey, String orderId, Instant createdAt) {
        this.idempotencyKey = idempotencyKey;
        this.orderId = orderId;
        this.createdAt = createdAt;
    }

    public String getIdempotencyKey() { return idempotencyKey; }
    public void setIdempotencyKey(String idempotencyKey) { this.idempotencyKey = idempotencyKey; }
    public String getOrderId() { return orderId; }
    public void setOrderId(String orderId) { this.orderId = orderId; }
    public Instant getCreatedAt() { return createdAt; }
    public void setCreatedAt(Instant createdAt) { this.createdAt = createdAt; }
}
