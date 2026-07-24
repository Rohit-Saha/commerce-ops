package com.commerceops.shipping.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "commerce.shipping")
public class ShippingProperties {

    private String provider = "simulated";
    private double failureRate = 0.0;
    private double defaultWeightKg = 0.5;
    private final OrderService orderService = new OrderService();
    private final SagaService sagaService = new SagaService();
    private final Shiprocket shiprocket = new Shiprocket();

    public String getProvider() {
        return provider;
    }

    public void setProvider(String provider) {
        this.provider = provider;
    }

    public boolean isShiprocket() {
        return "shiprocket".equalsIgnoreCase(provider);
    }

    public double getFailureRate() {
        return failureRate;
    }

    public void setFailureRate(double failureRate) {
        this.failureRate = failureRate;
    }

    public double getDefaultWeightKg() {
        return defaultWeightKg;
    }

    public void setDefaultWeightKg(double defaultWeightKg) {
        this.defaultWeightKg = defaultWeightKg;
    }

    public OrderService getOrderService() {
        return orderService;
    }

    public SagaService getSagaService() {
        return sagaService;
    }

    public Shiprocket getShiprocket() {
        return shiprocket;
    }

    public static class OrderService {
        private String baseUrl = "http://localhost:8081";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class SagaService {
        private String baseUrl = "http://localhost:8085";

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }
    }

    public static class Shiprocket {
        private String baseUrl = "https://apiv2.shiprocket.in";
        private String email = "";
        private String password = "";
        private String pickupLocation = "Primary";
        private String webhookToken = "";
        private int channelId = 0;

        public String getBaseUrl() {
            return baseUrl;
        }

        public void setBaseUrl(String baseUrl) {
            this.baseUrl = baseUrl;
        }

        public String getEmail() {
            return email;
        }

        public void setEmail(String email) {
            this.email = email;
        }

        public String getPassword() {
            return password;
        }

        public void setPassword(String password) {
            this.password = password;
        }

        public String getPickupLocation() {
            return pickupLocation;
        }

        public void setPickupLocation(String pickupLocation) {
            this.pickupLocation = pickupLocation;
        }

        public String getWebhookToken() {
            return webhookToken;
        }

        public void setWebhookToken(String webhookToken) {
            this.webhookToken = webhookToken;
        }

        public int getChannelId() {
            return channelId;
        }

        public void setChannelId(int channelId) {
            this.channelId = channelId;
        }
    }
}
