package com.commerceops.customer.web.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;

public record AddressRequest(
        @NotBlank @Size(max = 128) String recipientName,
        @NotBlank @Size(max = 256) String line1,
        @Size(max = 256) String line2,
        @NotBlank @Size(max = 128) String city,
        @NotBlank @Size(max = 64) String state,
        @NotBlank @Size(max = 32) String postalCode,
        @Size(max = 64) String country,
        Boolean isDefault
) {
}
