package com.commerceops.payment.razorpay;

import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Local/demo stand-in when {@code commerce.payment.provider=simulated}.
 * Accepts signatures of the form {@code sim-&lt;paymentId&gt;} or any non-blank signature in tests.
 */
@Component
@ConditionalOnProperty(name = "commerce.payment.provider", havingValue = "simulated", matchIfMissing = true)
public class SimulatedRazorpayClient implements RazorpayClientFacade {

    private final ConcurrentHashMap<String, Long> orders = new ConcurrentHashMap<>();

    @Override
    public CreatedOrder createOrder(long amountPaise, String currency, String receipt) {
        String id = "order_sim_" + UUID.randomUUID().toString().replace("-", "").substring(0, 14);
        orders.put(id, amountPaise);
        return new CreatedOrder(id, amountPaise, currency);
    }

    @Override
    public void capture(String providerPaymentId, long amountPaise, String currency) {
        // no-op
    }

    @Override
    public void refund(String providerPaymentId, long amountPaise) {
        // no-op
    }

    @Override
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        if (orderId == null || paymentId == null || signature == null) {
            return false;
        }
        if (!orders.containsKey(orderId) && !orderId.startsWith("order_sim_")) {
            return false;
        }
        // Accept Checkout-style sim signatures or a deterministic HMAC-like token
        return signature.startsWith("sim_")
                || signature.equals("simulated")
                || signature.getBytes(StandardCharsets.UTF_8).length > 8;
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        return signature != null && !signature.isBlank();
    }

    @Override
    public String keyId() {
        return "rzp_test_simulated";
    }
}
