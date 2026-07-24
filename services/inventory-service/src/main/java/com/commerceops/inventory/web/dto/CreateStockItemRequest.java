package com.commerceops.inventory.web.dto;

import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;

import java.math.BigDecimal;

public record CreateStockItemRequest(
        @NotBlank String sku,
        @NotBlank String name,
        @NotNull @DecimalMin(value = "0.00", inclusive = true) BigDecimal unitPrice,
        @Min(0) Integer availableQty
) {
}
