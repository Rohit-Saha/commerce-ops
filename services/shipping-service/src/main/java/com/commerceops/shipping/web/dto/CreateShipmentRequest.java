package com.commerceops.shipping.web.dto;

import jakarta.validation.constraints.NotBlank;

public record CreateShipmentRequest(@NotBlank String orderId) {}
