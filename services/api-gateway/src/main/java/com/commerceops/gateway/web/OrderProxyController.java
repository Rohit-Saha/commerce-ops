package com.commerceops.gateway.web;

import com.commerceops.common.web.ApiError;
import com.commerceops.gateway.service.ProxyGateway;
import com.commerceops.gateway.web.filter.ApiKeyAuthFilter;
import com.commerceops.gateway.web.filter.CustomerJwtFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@RestController
@RequestMapping("/api/orders")
public class OrderProxyController {

    private final RestClient orderClient;
    private final RestClient customerClient;
    private final RestClient paymentClient;
    private final ProxyGateway proxy;
    private final ObjectMapper objectMapper;

    public OrderProxyController(
            @Qualifier("orderRestClient") RestClient orderClient,
            @Qualifier("customerRestClient") RestClient customerClient,
            @Qualifier("paymentRestClient") RestClient paymentClient,
            ProxyGateway proxy,
            ObjectMapper objectMapper) {
        this.orderClient = orderClient;
        this.customerClient = customerClient;
        this.paymentClient = paymentClient;
        this.proxy = proxy;
        this.objectMapper = objectMapper;
    }

    @PostMapping
    public ResponseEntity<?> createOrder(
            HttpServletRequest request,
            @RequestHeader(value = "Idempotency-Key", required = false) String idempotencyKey,
            @RequestBody JsonNode body) {
        HttpHeaders forwardHeaders = new HttpHeaders();
        if (idempotencyKey != null && !idempotencyKey.isBlank()) {
            forwardHeaders.add("Idempotency-Key", idempotencyKey);
        }

        Object role = request.getAttribute(ApiKeyAuthFilter.API_KEY_ROLE_ATTR);
        if (role == ApiKeyAuthFilter.ApiKeyRole.STOREFRONT) {
            String customerId = (String) request.getAttribute(CustomerJwtFilter.CUSTOMER_ID_ATTR);
            try {
                return createStorefrontOrder(request, body, customerId, forwardHeaders);
            } catch (IllegalArgumentException ex) {
                return ResponseEntity.badRequest()
                        .body(ApiError.of(
                                HttpStatus.BAD_REQUEST.value(),
                                "Bad Request",
                                ex.getMessage() != null ? ex.getMessage() : "Please check your details and try again."));
            } catch (RestClientResponseException ex) {
                return ResponseEntity.status(ex.getStatusCode())
                        .headers(ex.getResponseHeaders() != null ? ex.getResponseHeaders() : new HttpHeaders())
                        .body(parseBody(ex));
            }
        }

        return proxy.post(orderClient, "/api/orders", body, forwardHeaders);
    }

    @GetMapping
    public ResponseEntity<JsonNode> listOrders() {
        return proxy.get(orderClient, "/api/orders");
    }

    @GetMapping("/mine")
    public ResponseEntity<JsonNode> listMyOrders(HttpServletRequest request) {
        String customerId = (String) request.getAttribute(CustomerJwtFilter.CUSTOMER_ID_ATTR);
        return proxy.get(orderClient, "/api/orders/by-customer/" + customerId);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> getOrder(HttpServletRequest request, @PathVariable String id) {
        ResponseEntity<JsonNode> response = proxy.get(orderClient, "/api/orders/" + id);
        Object role = request.getAttribute(ApiKeyAuthFilter.API_KEY_ROLE_ATTR);
        if (role == ApiKeyAuthFilter.ApiKeyRole.STOREFRONT) {
            String customerId = (String) request.getAttribute(CustomerJwtFilter.CUSTOMER_ID_ATTR);
            JsonNode body = ApiBodies.data(response.getBody());
            if (body != null && body.hasNonNull("customerId")
                    && !customerId.equals(body.get("customerId").asText())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiError.of(
                                HttpStatus.NOT_FOUND.value(),
                                "Not Found",
                                "We couldn’t find that order."));
            }
        }
        return response;
    }

    @PostMapping("/{id}/cancel")
    public ResponseEntity<?> cancelOrder(
            HttpServletRequest request,
            @PathVariable String id,
            @RequestBody(required = false) JsonNode body) {
        Object role = request.getAttribute(ApiKeyAuthFilter.API_KEY_ROLE_ATTR);
        if (role == ApiKeyAuthFilter.ApiKeyRole.STOREFRONT) {
            String customerId = (String) request.getAttribute(CustomerJwtFilter.CUSTOMER_ID_ATTR);
            ResponseEntity<JsonNode> existing = proxy.get(orderClient, "/api/orders/" + id);
            JsonNode order = ApiBodies.data(existing.getBody());
            if (order == null || !order.hasNonNull("customerId")
                    || !customerId.equals(order.get("customerId").asText())) {
                return ResponseEntity.status(HttpStatus.NOT_FOUND)
                        .body(ApiError.of(
                                HttpStatus.NOT_FOUND.value(),
                                "Not Found",
                                "We couldn’t find that order."));
            }
        }
        return proxy.post(orderClient, "/api/orders/" + id + "/cancel", body, null);
    }

    private ResponseEntity<?> createStorefrontOrder(
            HttpServletRequest request,
            JsonNode body,
            String customerId,
            HttpHeaders forwardHeaders) {
        requireText(body, "razorpayOrderId");
        requireText(body, "razorpayPaymentId");
        requireText(body, "razorpaySignature");

        JsonNode orderBody = resolveStorefrontOrderBody(request, body, customerId);
        forwardHeaders.add("X-Defer-Fulfillment", "true");

        ResponseEntity<JsonNode> created = proxy.post(orderClient, "/api/orders", orderBody, forwardHeaders);
        JsonNode order = ApiBodies.data(created.getBody());
        if (order == null || !order.hasNonNull("id")) {
            return created;
        }
        String orderId = order.get("id").asText();

        ObjectNode authorizeBody = objectMapper.createObjectNode();
        authorizeBody.put("orderId", orderId);
        authorizeBody.put("razorpayOrderId", body.get("razorpayOrderId").asText());
        authorizeBody.put("razorpayPaymentId", body.get("razorpayPaymentId").asText());
        authorizeBody.put("razorpaySignature", body.get("razorpaySignature").asText());
        if (order.has("totalAmount")) {
            authorizeBody.set("amount", order.get("totalAmount"));
        }
        if (order.has("currency")) {
            authorizeBody.set("currency", order.get("currency"));
        }

        try {
            proxy.post(paymentClient, "/api/payments/razorpay/authorize", authorizeBody, null);
        } catch (RestClientResponseException ex) {
            try {
                ObjectNode abandon = objectMapper.createObjectNode();
                abandon.put("reason", "payment authorization failed");
                proxy.post(orderClient, "/api/orders/" + orderId + "/abandon", abandon, null);
            } catch (Exception ignored) {
                // best-effort abandon
            }
            return ResponseEntity.status(ex.getStatusCode())
                    .headers(ex.getResponseHeaders() != null ? ex.getResponseHeaders() : new HttpHeaders())
                    .body(parseBody(ex));
        }

        return proxy.post(orderClient, "/api/orders/" + orderId + "/start-fulfillment", null, null);
    }

    private JsonNode resolveStorefrontOrderBody(HttpServletRequest request, JsonNode body, String customerId) {
        if (body == null || !body.isObject()) {
            throw new IllegalArgumentException("Order body is required");
        }
        if (!body.has("currency") || !body.has("lines")) {
            throw new IllegalArgumentException("currency and lines are required");
        }

        ObjectNode orderBody = objectMapper.createObjectNode();
        orderBody.put("customerId", customerId);
        orderBody.set("currency", body.get("currency"));
        orderBody.set("lines", body.get("lines"));

        JsonNode addressIdNode = body.get("shippingAddressId");
        JsonNode inlineAddress = body.get("shippingAddress");
        ObjectNode snapshot;
        if (addressIdNode != null && addressIdNode.isTextual() && !addressIdNode.asText().isBlank()) {
            snapshot = snapshotFromSavedAddress(request, addressIdNode.asText());
        } else if (inlineAddress != null && inlineAddress.isObject()) {
            snapshot = snapshotFromNewAddress(request, inlineAddress);
        } else {
            throw new IllegalArgumentException("shippingAddressId or shippingAddress is required");
        }
        orderBody.set("shippingAddress", snapshot);
        return orderBody;
    }

    private ObjectNode snapshotFromSavedAddress(HttpServletRequest request, String addressId) {
        HttpHeaders headers = bearerHeaders(request);
        ResponseEntity<JsonNode> response = proxy.get(
                customerClient, "/api/customers/me/addresses/" + addressId, headers);
        return toSnapshot(ApiBodies.data(response.getBody()));
    }

    private ObjectNode snapshotFromNewAddress(HttpServletRequest request, JsonNode inlineAddress) {
        ObjectNode createBody = objectMapper.createObjectNode();
        copyText(createBody, inlineAddress, "recipientName");
        copyText(createBody, inlineAddress, "line1");
        copyText(createBody, inlineAddress, "line2");
        copyText(createBody, inlineAddress, "city");
        copyText(createBody, inlineAddress, "state");
        copyText(createBody, inlineAddress, "postalCode");
        copyText(createBody, inlineAddress, "country");
        if (inlineAddress.has("isDefault")) {
            createBody.set("isDefault", inlineAddress.get("isDefault"));
        }

        HttpHeaders headers = bearerHeaders(request);
        ResponseEntity<JsonNode> created = proxy.post(
                customerClient, "/api/customers/me/addresses", createBody, headers);
        return toSnapshot(ApiBodies.data(created.getBody()));
    }

    private ObjectNode toSnapshot(JsonNode address) {
        if (address == null || !address.isObject()) {
            throw new IllegalArgumentException("Address could not be resolved");
        }
        requireText(address, "recipientName");
        requireText(address, "line1");
        requireText(address, "city");
        requireText(address, "state");
        requireText(address, "postalCode");

        ObjectNode snapshot = objectMapper.createObjectNode();
        snapshot.put("recipientName", address.get("recipientName").asText());
        snapshot.put("line1", address.get("line1").asText());
        if (address.hasNonNull("line2")) {
            snapshot.put("line2", address.get("line2").asText());
        }
        snapshot.put("city", address.get("city").asText());
        snapshot.put("state", address.get("state").asText());
        snapshot.put("postalCode", address.get("postalCode").asText());
        snapshot.put("country", address.hasNonNull("country") ? address.get("country").asText() : "IN");
        if (address.hasNonNull("id")) {
            snapshot.put("sourceAddressId", address.get("id").asText());
        }
        return snapshot;
    }

    private static void requireText(JsonNode node, String field) {
        if (node == null || !node.hasNonNull(field) || node.get(field).asText().isBlank()) {
            throw new IllegalArgumentException(field + " is required");
        }
    }

    private static void copyText(ObjectNode target, JsonNode source, String field) {
        if (source.has(field) && !source.get(field).isNull()) {
            target.put(field, source.get(field).asText());
        }
    }

    private static HttpHeaders bearerHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Object token = request.getAttribute(CustomerJwtFilter.CUSTOMER_TOKEN_ATTR);
        if (token != null) {
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        } else {
            String header = request.getHeader("Authorization");
            if (header != null && !header.isBlank()) {
                headers.add(HttpHeaders.AUTHORIZATION, header);
            }
        }
        return headers;
    }

    private JsonNode parseBody(RestClientResponseException ex) {
        try {
            return objectMapper.readTree(ex.getResponseBodyAsString());
        } catch (Exception ignored) {
            return objectMapper.valueToTree(
                    ApiError.of(
                            ex.getStatusCode().value(),
                            "Error",
                            "Something went wrong on our side. Please try again in a moment."));
        }
    }
}
