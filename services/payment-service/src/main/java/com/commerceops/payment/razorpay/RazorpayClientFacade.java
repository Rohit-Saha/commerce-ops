package com.commerceops.payment.razorpay;

import java.math.BigDecimal;

public interface RazorpayClientFacade {

    record CreatedOrder(String razorpayOrderId, long amountPaise, String currency) {}

    CreatedOrder createOrder(long amountPaise, String currency, String receipt);

    void capture(String providerPaymentId, long amountPaise, String currency);

    void refund(String providerPaymentId, long amountPaise);

    boolean verifyPaymentSignature(String orderId, String paymentId, String signature);

    boolean verifyWebhookSignature(String payload, String signature);

    String keyId();

    static long toPaise(BigDecimal amount) {
        return amount.movePointRight(2).setScale(0, java.math.RoundingMode.HALF_UP).longValueExact();
    }
}
