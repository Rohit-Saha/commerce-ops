package com.commerceops.gateway.web;

import com.commerceops.gateway.service.ProxyGateway;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/payments")
public class PaymentProxyController {

    private final RestClient paymentClient;
    private final ProxyGateway proxy;

    public PaymentProxyController(@Qualifier("paymentRestClient") RestClient paymentClient, ProxyGateway proxy) {
        this.paymentClient = paymentClient;
        this.proxy = proxy;
    }

    @GetMapping
    public ResponseEntity<JsonNode> list() {
        return proxy.get(paymentClient, "/api/payments");
    }

    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<JsonNode> byOrder(@PathVariable String orderId) {
        return proxy.get(paymentClient, "/api/payments/by-order/" + encode(orderId));
    }

    @PostMapping("/chaos")
    public ResponseEntity<JsonNode> setChaos(@RequestParam double failureRate) {
        return proxy.post(paymentClient, "/api/payments/chaos?failureRate=" + failureRate, null, null);
    }

    @PostMapping("/razorpay/orders")
    public ResponseEntity<JsonNode> createRazorpayOrder(@RequestBody JsonNode body) {
        return proxy.post(paymentClient, "/api/payments/razorpay/orders", body, null);
    }

    @PostMapping("/razorpay/authorize")
    public ResponseEntity<JsonNode> authorize(@RequestBody JsonNode body) {
        return proxy.post(paymentClient, "/api/payments/razorpay/authorize", body, null);
    }

    @PostMapping("/webhooks/razorpay")
    public ResponseEntity<JsonNode> razorpayWebhook(
            @RequestBody String body,
            @RequestHeader(value = "X-Razorpay-Signature", required = false) String signature) {
        HttpHeaders headers = new HttpHeaders();
        if (signature != null) {
            headers.add("X-Razorpay-Signature", signature);
        }
        headers.add(HttpHeaders.CONTENT_TYPE, "application/json");
        return proxy.post(paymentClient, "/api/payments/webhooks/razorpay", body, headers);
    }

    private String encode(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
