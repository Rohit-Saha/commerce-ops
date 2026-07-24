# returns-service (Phase 2 stub)

Port: **8086**

This module is a placeholder for the returns/RMA capability. It compiles and
runs like every other service, but it does not yet implement any business
logic, persistence, or messaging -- it exists so the module is wired into the
Maven reactor, the Docker/runbook tooling, and the admin UI's service map
ahead of the real implementation.

## Current endpoints

| Method | Path                         | Behavior                                                        |
|--------|------------------------------|-------------------------------------------------------------------|
| GET    | `/api/returns`                | Always returns an empty JSON array `[]`                          |
| GET    | `/api/returns/health-message` | Returns `{"status":"STUB","phase":2,"message":"..."}`            |
| GET    | `/actuator/health`            | Standard Spring Boot Actuator health check                       |
| GET    | `/actuator/prometheus`        | Prometheus scrape endpoint                                        |

## Planned Phase 2 scope

The returns-service will own the **RMA -> restock -> refund** saga, mirroring
the orchestration pattern already used for order fulfillment:

1. **Return Requested** -- a customer or CSR opens an RMA against a completed
   order/shipment. The service validates the order/shipment exist and are
   eligible for return.
2. **Return Authorized** -- the RMA is approved (auto or manual) and an
   `returns_db` record is created with a `PENDING_RECEIPT` status.
3. **Item Received / Restock** -- once the warehouse confirms receipt, the
   service publishes a `RestockInventory` command consumed by
   `inventory-service` to add the returned quantity back to stock.
4. **Refund Issued** -- after restock succeeds, a `RefundPayment` command is
   published to `payment-service` (the same command already used by the
   order cancellation saga) and the RMA is marked `COMPLETED`.
5. **Compensation** -- if the refund fails, the restock is left in place but
   the RMA is marked `FAILED_NEEDS_ATTENTION` for manual review, following the
   same terminal-state convention as `saga-orchestrator`.

This will require adding `spring-boot-starter-data-jpa`, `spring-kafka`,
Flyway migrations against a new `returns_db`, and `common-idempotency` /
`common-kafka` (outbox) once the saga steps above are implemented -- none of
which are needed for this stub.
