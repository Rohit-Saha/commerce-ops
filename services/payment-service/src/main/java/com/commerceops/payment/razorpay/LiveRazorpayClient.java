package com.commerceops.payment.razorpay;

import com.commerceops.payment.config.PaymentProperties;
import com.razorpay.RazorpayClient;
import com.razorpay.RazorpayException;
import com.razorpay.Utils;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.json.JSONObject;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.stereotype.Component;

import java.util.function.Supplier;

@Component
@ConditionalOnProperty(name = "commerce.payment.provider", havingValue = "razorpay")
public class LiveRazorpayClient implements RazorpayClientFacade {

    private final RazorpayClient client;
    private final PaymentProperties properties;
    private final CircuitBreaker circuitBreaker;

    public LiveRazorpayClient(PaymentProperties properties, CircuitBreakerRegistry circuitBreakerRegistry)
            throws RazorpayException {
        this.properties = properties;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("razorpay");
        String keyId = properties.getRazorpay().getKeyId();
        String keySecret = properties.getRazorpay().getKeySecret();
        if (keyId == null || keyId.isBlank() || keySecret == null || keySecret.isBlank()) {
            throw new IllegalStateException(
                    "commerce.payment.provider=razorpay requires RAZORPAY_KEY_ID and RAZORPAY_KEY_SECRET");
        }
        this.client = new RazorpayClient(keyId, keySecret);
    }

    @Override
    public CreatedOrder createOrder(long amountPaise, String currency, String receipt) {
        return execute(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("amount", amountPaise);
                request.put("currency", currency);
                request.put("receipt", receipt == null ? "rcpt" : receipt.substring(0, Math.min(40, receipt.length())));
                request.put("payment_capture", 0);
                com.razorpay.Order order = client.orders.create(request);
                return new CreatedOrder(order.get("id"), amountPaise, currency);
            } catch (RazorpayException ex) {
                throw new IllegalStateException("Razorpay order create failed: " + ex.getMessage(), ex);
            }
        });
    }

    @Override
    public void capture(String providerPaymentId, long amountPaise, String currency) {
        execute(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("amount", amountPaise);
                request.put("currency", currency);
                client.payments.capture(providerPaymentId, request);
                return null;
            } catch (RazorpayException ex) {
                throw new IllegalStateException("Razorpay capture failed: " + ex.getMessage(), ex);
            }
        });
    }

    @Override
    public void refund(String providerPaymentId, long amountPaise) {
        execute(() -> {
            try {
                JSONObject request = new JSONObject();
                request.put("amount", amountPaise);
                client.payments.refund(providerPaymentId, request);
                return null;
            } catch (RazorpayException ex) {
                throw new IllegalStateException("Razorpay refund failed: " + ex.getMessage(), ex);
            }
        });
    }

    @Override
    public boolean verifyPaymentSignature(String orderId, String paymentId, String signature) {
        try {
            JSONObject options = new JSONObject();
            options.put("razorpay_order_id", orderId);
            options.put("razorpay_payment_id", paymentId);
            options.put("razorpay_signature", signature);
            Utils.verifyPaymentSignature(options, properties.getRazorpay().getKeySecret());
            return true;
        } catch (RazorpayException ex) {
            return false;
        }
    }

    @Override
    public boolean verifyWebhookSignature(String payload, String signature) {
        try {
            String secret = properties.getRazorpay().getWebhookSecret();
            if (secret == null || secret.isBlank()) {
                return false;
            }
            Utils.verifyWebhookSignature(payload, signature, secret);
            return true;
        } catch (RazorpayException ex) {
            return false;
        }
    }

    @Override
    public String keyId() {
        return properties.getRazorpay().getKeyId();
    }

    private <T> T execute(Supplier<T> supplier) {
        try {
            return CircuitBreaker.decorateSupplier(circuitBreaker, supplier).get();
        } catch (CallNotPermittedException ex) {
            throw new IllegalStateException(
                    "Payment provider temporarily unavailable; try again shortly.", ex);
        }
    }
}
