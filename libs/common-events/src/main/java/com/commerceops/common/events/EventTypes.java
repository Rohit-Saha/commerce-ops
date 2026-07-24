package com.commerceops.common.events;

public final class EventTypes {
    public static final String ORDER_CREATED = "OrderCreated";
    public static final String ORDER_STATUS_CHANGED = "OrderStatusChanged";
    public static final String ORDER_CANCEL_REQUESTED = "OrderCancelRequested";

    public static final String RESERVE_INVENTORY = "ReserveInventory";
    public static final String RELEASE_INVENTORY = "ReleaseInventory";
    public static final String INVENTORY_RESERVED = "InventoryReserved";
    public static final String INVENTORY_RESERVE_FAILED = "InventoryReserveFailed";
    public static final String INVENTORY_RELEASED = "InventoryReleased";
    public static final String STOCK_ITEM_CHANGED = "StockItemChanged";

    public static final String CAPTURE_PAYMENT = "CapturePayment";
    public static final String REFUND_PAYMENT = "RefundPayment";
    public static final String PAYMENT_CAPTURED = "PaymentCaptured";
    public static final String PAYMENT_FAILED = "PaymentFailed";
    public static final String PAYMENT_REFUNDED = "PaymentRefunded";

    public static final String CREATE_SHIPMENT = "CreateShipment";
    public static final String SHIPMENT_CREATED = "ShipmentCreated";
    public static final String SHIPMENT_FAILED = "ShipmentFailed";
    public static final String SHIPMENT_STATUS_UPDATED = "ShipmentStatusUpdated";

    public static final String INVOICE_ISSUED = "InvoiceIssued";

    public static final String SAGA_STEP_COMPLETED = "SagaStepCompleted";
    public static final String SAGA_COMPLETED = "SagaCompleted";
    public static final String SAGA_COMPENSATED = "SagaCompensated";
    public static final String SAGA_FAILED = "SagaFailed";

    private EventTypes() {}
}
