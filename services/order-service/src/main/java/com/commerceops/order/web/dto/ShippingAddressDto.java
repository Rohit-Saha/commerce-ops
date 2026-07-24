package com.commerceops.order.web.dto;

import jakarta.validation.constraints.Size;

public record ShippingAddressDto(
        @Size(max = 128) String recipientName,
        @Size(max = 256) String line1,
        @Size(max = 256) String line2,
        @Size(max = 128) String city,
        @Size(max = 64) String state,
        @Size(max = 32) String postalCode,
        @Size(max = 64) String country,
        @Size(max = 36) String sourceAddressId
) {
}
