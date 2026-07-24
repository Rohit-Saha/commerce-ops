package com.commerceops.invoice.web.dto;

import java.math.BigDecimal;

public record InvoiceLineResponse(
        int lineNo,
        String sku,
        String description,
        int quantity,
        BigDecimal unitPrice,
        BigDecimal lineGross,
        BigDecimal taxable,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst
) {
}
