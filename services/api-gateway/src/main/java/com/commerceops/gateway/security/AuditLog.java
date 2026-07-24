package com.commerceops.gateway.security;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Structured security audit lines (no secrets / minimal PII).
 */
public final class AuditLog {

    private static final Logger log = LoggerFactory.getLogger("commerce.audit");

    private AuditLog() {}

    public static void authSuccess(String action, String actor) {
        log.info("event=auth_success action={} actor={}", action, sanitize(actor));
    }

    public static void authFailure(String action, String actor) {
        log.warn("event=auth_failure action={} actor={}", action, sanitize(actor));
    }

    public static void mutation(String action, String actor, String resourceType, String resourceId) {
        log.info("event=mutation action={} actor={} resourceType={} resourceId={}",
                action, sanitize(actor), resourceType, sanitize(resourceId));
    }

    private static String sanitize(String value) {
        if (value == null || value.isBlank()) {
            return "-";
        }
        return value.replaceAll("[\\r\\n\\t]", "_");
    }
}
