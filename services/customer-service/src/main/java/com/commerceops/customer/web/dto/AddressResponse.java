package com.commerceops.customer.web.dto;

import java.time.Instant;

public record AddressResponse(
        String id,
        String recipientName,
        String line1,
        String line2,
        String city,
        String state,
        String postalCode,
        String country,
        boolean isDefault,
        Instant createdAt,
        Instant updatedAt
) {
}
