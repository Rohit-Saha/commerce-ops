package com.commerceops.customer.web.dto;

import jakarta.validation.constraints.Size;

public record UpdateProfileRequest(
        @Size(min = 1, max = 128) String displayName,
        String currentPassword,
        @Size(min = 8, max = 128) String newPassword
) {
}
