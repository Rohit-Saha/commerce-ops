package com.commerceops.customer.web.dto;

import java.time.Instant;

public record CustomerProfileResponse(
        String id,
        String email,
        String displayName,
        Instant createdAt
) {
}
