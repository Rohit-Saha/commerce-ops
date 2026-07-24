package com.commerceops.payment.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "commerce.payment")
public class PaymentProperties {

    /**
     * {@code simulated} — instant capture with chaos knobs (admin demos).
     * {@code razorpay} — authorize on checkout, capture after inventory reserve.
     */
    private String provider = "simulated";

    private final Razorpay razorpay = new Razorpay();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public Razorpay getRazorpay() {
        return razorpay;
    }

    public boolean isRazorpay() {
        return "razorpay".equalsIgnoreCase(provider);
    }

    public static class Razorpay {
        private String keyId = "";
        private String keySecret = "";
        private String webhookSecret = "";

        public String getKeyId() {
            return keyId;
        }

        public void setKeyId(String keyId) {
            this.keyId = keyId;
        }

        public String getKeySecret() {
            return keySecret;
        }

        public void setKeySecret(String keySecret) {
            this.keySecret = keySecret;
        }

        public String getWebhookSecret() {
            return webhookSecret;
        }

        public void setWebhookSecret(String webhookSecret) {
            this.webhookSecret = webhookSecret;
        }
    }
}
