package com.commerceops.common.events;

public final class Topics {
    public static final String ORDER_EVENTS = "order.events";
    public static final String INVENTORY_EVENTS = "inventory.events";
    public static final String PAYMENT_EVENTS = "payment.events";
    public static final String SHIPPING_EVENTS = "shipping.events";
    public static final String SAGA_EVENTS = "saga.events";
    public static final String INVOICE_EVENTS = "invoice.events";

    public static final String INVENTORY_COMMANDS = "inventory.commands";
    public static final String PAYMENT_COMMANDS = "payment.commands";
    public static final String SHIPPING_COMMANDS = "shipping.commands";

    public static final String ORDER_EVENTS_DLQ = "order.events.dlq";
    public static final String INVENTORY_EVENTS_DLQ = "inventory.events.dlq";
    public static final String PAYMENT_EVENTS_DLQ = "payment.events.dlq";
    public static final String SHIPPING_EVENTS_DLQ = "shipping.events.dlq";
    public static final String INVOICE_EVENTS_DLQ = "invoice.events.dlq";

    private Topics() {}
}
