package com.commerceops.gateway.web;

import com.commerceops.gateway.service.ProxyGateway;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/sagas")
public class SagaProxyController {

    private final RestClient sagaClient;
    private final ProxyGateway proxy;

    public SagaProxyController(@Qualifier("sagaRestClient") RestClient sagaClient, ProxyGateway proxy) {
        this.sagaClient = sagaClient;
        this.proxy = proxy;
    }

    @GetMapping
    public ResponseEntity<JsonNode> list() {
        return proxy.get(sagaClient, "/api/sagas");
    }

    @GetMapping("/{id}")
    public ResponseEntity<JsonNode> getById(@PathVariable String id) {
        return proxy.get(sagaClient, "/api/sagas/" + encode(id));
    }

    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<JsonNode> byOrder(@PathVariable String orderId) {
        return proxy.get(sagaClient, "/api/sagas/by-order/" + encode(orderId));
    }

    private String encode(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
