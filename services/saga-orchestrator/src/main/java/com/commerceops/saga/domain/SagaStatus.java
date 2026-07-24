package com.commerceops.saga.domain;

public enum SagaStatus {
    STARTED,
    RESERVING,
    RESERVED,
    PAYING,
    PAID,
    SHIPPING,
    COMPLETED,
    COMPENSATING,
    COMPENSATED,
    FAILED_NEEDS_ATTENTION;

    public boolean isTerminal() {
        return this == COMPLETED || this == COMPENSATED || this == FAILED_NEEDS_ATTENTION;
    }
}
