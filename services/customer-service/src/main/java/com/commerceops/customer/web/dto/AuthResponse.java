package com.commerceops.customer.web.dto;

public record AuthResponse(
        String token,
        CustomerProfileResponse customer
) {
}
