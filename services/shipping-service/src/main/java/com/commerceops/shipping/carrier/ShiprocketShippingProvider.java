package com.commerceops.shipping.carrier;

import com.commerceops.common.events.Payloads;
import com.commerceops.shipping.config.ShippingProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ArrayNode;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.MediaType;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

import java.math.BigDecimal;
import java.net.http.HttpClient;
import java.time.Duration;
import java.time.Instant;
import java.util.concurrent.atomic.AtomicReference;
import java.util.function.Supplier;

@Component
public class ShiprocketShippingProvider implements ShippingProvider {

    private static final Logger log = LoggerFactory.getLogger(ShiprocketShippingProvider.class);

    private final ShippingProperties properties;
    private final ObjectMapper objectMapper;
    private final RestClient restClient;
    private final CircuitBreaker circuitBreaker;
    private final AtomicReference<CachedToken> tokenCache = new AtomicReference<>();

    public ShiprocketShippingProvider(
            ShippingProperties properties,
            ObjectMapper objectMapper,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.properties = properties;
        this.objectMapper = objectMapper;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("shiprocket");
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(Duration.ofSeconds(2))
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(Duration.ofSeconds(10));
        this.restClient = RestClient.builder()
                .baseUrl(properties.getShiprocket().getBaseUrl())
                .requestFactory(factory)
                .build();
    }

    @Override
    public BookingResult book(BookingRequest request) {
        Payloads.ShippingAddress address = request.address();
        if (address == null
                || isBlank(address.recipientName())
                || isBlank(address.line1())
                || isBlank(address.city())
                || isBlank(address.postalCode())) {
            return BookingResult.failed("shiprocket", "Shipping address is incomplete for carrier booking");
        }

        try {
            String token = authenticate();
            ObjectNode body = buildCreateOrderBody(request);
            JsonNode response = execute(() -> restClient.post()
                    .uri("/v1/external/orders/create/adhoc")
                    .contentType(MediaType.APPLICATION_JSON)
                    .header("Authorization", "Bearer " + token)
                    .body(body)
                    .retrieve()
                    .body(JsonNode.class));

            if (response == null) {
                return BookingResult.failed("shiprocket", "Empty response from Shiprocket");
            }
            if (response.path("status_code").asInt(0) >= 400
                    || response.hasNonNull("message") && !response.path("order_id").canConvertToLong()) {
                String msg = response.path("message").asText("Shiprocket order create failed");
                if (response.has("errors")) {
                    msg = msg + ": " + response.get("errors");
                }
                return BookingResult.failed("shiprocket", msg);
            }

            String carrierOrderId = textOrNull(response, "order_id");
            if (carrierOrderId == null) {
                carrierOrderId = textOrNull(response.path("payload"), "order_id");
            }
            String awb = firstNonBlank(
                    textOrNull(response, "awb_code"),
                    textOrNull(response.path("payload"), "awb_code"),
                    textOrNull(response, "shipment_id"));
            String label = firstNonBlank(
                    textOrNull(response, "label_url"),
                    textOrNull(response.path("payload"), "label_url"));

            if (awb == null || awb.isBlank()) {
                awb = "SR-" + (carrierOrderId != null ? carrierOrderId : Instant.now().toEpochMilli());
            }
            return BookingResult.ok("shiprocket", carrierOrderId, awb, label);
        } catch (CallNotPermittedException ex) {
            log.warn("Shiprocket circuit open — failing booking for order {}", request.orderId());
            return BookingResult.failed("shiprocket", "Carrier temporarily unavailable; try again shortly.");
        } catch (RestClientResponseException ex) {
            log.warn("Shiprocket booking HTTP {} for order {}: {}",
                    ex.getStatusCode().value(), request.orderId(), ex.getResponseBodyAsString());
            return BookingResult.failed("shiprocket", "Shiprocket booking failed (" + ex.getStatusCode().value() + ")");
        } catch (Exception ex) {
            log.error("Shiprocket booking failed for order {}: {}", request.orderId(), ex.getMessage());
            return BookingResult.failed("shiprocket", "Shiprocket booking failed: " + ex.getMessage());
        }
    }

    public boolean verifyWebhookToken(String provided) {
        String expected = properties.getShiprocket().getWebhookToken();
        boolean requireSecret = Boolean.parseBoolean(
                System.getenv().getOrDefault("REQUIRE_WEBHOOK_SECRETS", "false"));
        if (expected == null || expected.isBlank()) {
            if (requireSecret) {
                return false;
            }
            return true;
        }
        return expected.equals(provided);
    }

    private String authenticate() {
        CachedToken cached = tokenCache.get();
        if (cached != null && cached.expiresAt().isAfter(Instant.now().plusSeconds(60))) {
            return cached.token();
        }
        ShippingProperties.Shiprocket cfg = properties.getShiprocket();
        if (isBlank(cfg.getEmail()) || isBlank(cfg.getPassword())) {
            throw new IllegalStateException(
                    "commerce.shipping.provider=shiprocket requires SHIPROCKET_EMAIL and SHIPROCKET_PASSWORD");
        }
        ObjectNode login = objectMapper.createObjectNode();
        login.put("email", cfg.getEmail());
        login.put("password", cfg.getPassword());
        JsonNode response = execute(() -> restClient.post()
                .uri("/v1/external/auth/login")
                .contentType(MediaType.APPLICATION_JSON)
                .body(login)
                .retrieve()
                .body(JsonNode.class));
        if (response == null || isBlank(response.path("token").asText(null))) {
            throw new IllegalStateException("Shiprocket login failed");
        }
        String token = response.path("token").asText();
        tokenCache.set(new CachedToken(token, Instant.now().plusSeconds(8 * 3600)));
        return token;
    }

    private <T> T execute(Supplier<T> supplier) {
        return CircuitBreaker.decorateSupplier(circuitBreaker, supplier).get();
    }

    private ObjectNode buildCreateOrderBody(BookingRequest request) {
        Payloads.ShippingAddress address = request.address();
        ObjectNode body = objectMapper.createObjectNode();
        body.put("order_id", request.orderId());
        body.put("order_date", Instant.now().toString().substring(0, 10));
        body.put("pickup_location", properties.getShiprocket().getPickupLocation());
        body.put("billing_customer_name", address.recipientName());
        body.put("billing_last_name", ".");
        body.put("billing_address", address.line1());
        if (!isBlank(address.line2())) {
            body.put("billing_address_2", address.line2());
        }
        body.put("billing_city", address.city());
        body.put("billing_pincode", address.postalCode());
        body.put("billing_state", address.state() != null ? address.state() : "");
        body.put("billing_country", address.country() != null ? address.country() : "India");
        body.put("billing_email", "orders+" + request.orderId() + "@northline.local");
        body.put("billing_phone", "9999999999");
        body.put("shipping_is_billing", true);
        body.put("payment_method", "Prepaid");
        body.put("sub_total", sum(request));
        body.put("length", 10);
        body.put("breadth", 10);
        body.put("height", 10);
        body.put("weight", request.weightKg() != null
                ? request.weightKg().doubleValue()
                : properties.getDefaultWeightKg());
        if (properties.getShiprocket().getChannelId() > 0) {
            body.put("channel_id", properties.getShiprocket().getChannelId());
        }

        ArrayNode items = body.putArray("order_items");
        for (Payloads.OrderLine line : request.lines()) {
            ObjectNode item = items.addObject();
            item.put("name", line.sku());
            item.put("sku", line.sku());
            item.put("units", line.quantity());
            item.put("selling_price", line.unitPrice() != null ? line.unitPrice().doubleValue() : 1);
        }
        return body;
    }

    private static double sum(BookingRequest request) {
        BigDecimal total = BigDecimal.ZERO;
        for (Payloads.OrderLine line : request.lines()) {
            if (line.unitPrice() != null) {
                total = total.add(line.unitPrice().multiply(BigDecimal.valueOf(line.quantity())));
            }
        }
        return total.doubleValue();
    }

    private static boolean isBlank(String value) {
        return value == null || value.isBlank();
    }

    private static String textOrNull(JsonNode node, String field) {
        if (node == null || !node.has(field) || node.get(field).isNull()) {
            return null;
        }
        String value = node.get(field).asText(null);
        return value == null || value.isBlank() ? null : value;
    }

    private static String firstNonBlank(String... values) {
        for (String value : values) {
            if (value != null && !value.isBlank()) {
                return value;
            }
        }
        return null;
    }

    private record CachedToken(String token, Instant expiresAt) {}
}
