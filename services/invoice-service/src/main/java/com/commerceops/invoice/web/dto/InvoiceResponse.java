package com.commerceops.invoice.web.dto;

import java.math.BigDecimal;
import java.time.Instant;
import java.util.List;

public record InvoiceResponse(
        Long id,
        String invoiceNumber,
        String orderId,
        String shipmentId,
        String customerId,
        String currency,
        BigDecimal subtotal,
        BigDecimal cgst,
        BigDecimal sgst,
        BigDecimal igst,
        BigDecimal total,
        String status,
        String buyerName,
        String buyerLine1,
        String buyerLine2,
        String buyerCity,
        String buyerState,
        String buyerPostalCode,
        String buyerCountry,
        String sellerLegalName,
        String sellerGstin,
        String sellerAddress,
        String sellerState,
        String sellerStateCode,
        String paymentRef,
        List<InvoiceLineResponse> lines,
        Instant createdAt
) {
}
