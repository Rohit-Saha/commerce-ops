package com.commerceops.payment.web.dto;

public record CreateRazorpayOrderResponse(
        String keyId,
        String razorpayOrderId,
        long amountPaise,
        String currency,
        String provider
) {
}
