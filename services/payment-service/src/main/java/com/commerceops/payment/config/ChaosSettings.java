package com.commerceops.payment.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Component;

/**
 * Mutable chaos-engineering knob for payment capture failures. Seeded from
 * {@code commerce.payment.failure-rate} and adjustable at runtime via
 * {@code POST /api/payments/chaos}.
 */
@Component
public class ChaosSettings {

    private volatile double failureRate;

    public ChaosSettings(@Value("${commerce.payment.failure-rate:0.0}") double failureRate) {
        this.failureRate = failureRate;
    }

    public double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = Math.max(0.0, Math.min(1.0, failureRate));
    }
}
