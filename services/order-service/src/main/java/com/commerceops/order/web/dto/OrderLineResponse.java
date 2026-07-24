package com.commerceops.order.web.dto;

import java.math.BigDecimal;

public record OrderLineResponse(String sku, int quantity, BigDecimal unitPrice) {
}
