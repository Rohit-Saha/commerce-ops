package com.commerceops.gateway.web;

import com.commerceops.gateway.service.ProxyGateway;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/inventory")
public class InventoryProxyController {

    private final RestClient inventoryClient;
    private final ProxyGateway proxy;

    public InventoryProxyController(
            @Qualifier("inventoryRestClient") RestClient inventoryClient, ProxyGateway proxy) {
        this.inventoryClient = inventoryClient;
        this.proxy = proxy;
    }

    @GetMapping
    public ResponseEntity<JsonNode> listStock() {
        return proxy.get(inventoryClient, "/api/inventory");
    }

    @GetMapping("/{sku}")
    public ResponseEntity<JsonNode> getStock(@PathVariable String sku) {
        return proxy.get(inventoryClient, "/api/inventory/" + encode(sku));
    }

    @PostMapping
    public ResponseEntity<JsonNode> createStock(@RequestBody JsonNode body) {
        return proxy.post(inventoryClient, "/api/inventory", body, null);
    }

    @PutMapping("/{sku}")
    public ResponseEntity<JsonNode> updateStock(@PathVariable String sku, @RequestBody JsonNode body) {
        return proxy.put(inventoryClient, "/api/inventory/" + encode(sku), body, null);
    }

    @DeleteMapping("/{sku}")
    public ResponseEntity<Void> softDeleteStock(@PathVariable String sku) {
        return proxy.delete(inventoryClient, "/api/inventory/" + encode(sku));
    }

    @PostMapping("/{sku}/restock")
    public ResponseEntity<JsonNode> restock(@PathVariable String sku, @RequestParam int qty) {
        return proxy.post(inventoryClient, "/api/inventory/" + encode(sku) + "/restock?qty=" + qty, null, null);
    }

    private String encode(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
