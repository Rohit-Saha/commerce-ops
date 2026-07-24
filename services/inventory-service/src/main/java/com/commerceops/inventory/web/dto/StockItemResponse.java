package com.commerceops.inventory.web.dto;

import java.math.BigDecimal;

public record StockItemResponse(
        String sku,
        String name,
        int availableQty,
        int reservedQty,
        BigDecimal unitPrice
) {
}
