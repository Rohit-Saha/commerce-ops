package com.commerceops.payment.web.dto;

import jakarta.validation.constraints.NotBlank;

public record AuthorizeRazorpayRequest(
        @NotBlank
        String orderId,

        @NotBlank
        String razorpayOrderId,

        @NotBlank
        String razorpayPaymentId,

        @NotBlank
        String razorpaySignature,

        java.math.BigDecimal amount,

        String currency
) {
}
