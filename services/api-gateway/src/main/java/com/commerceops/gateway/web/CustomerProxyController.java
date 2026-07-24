package com.commerceops.gateway.web;

import com.commerceops.gateway.service.ProxyGateway;
import com.commerceops.gateway.web.filter.CustomerJwtFilter;
import com.fasterxml.jackson.databind.JsonNode;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;

@RestController
@RequestMapping("/api/customers")
public class CustomerProxyController {

    private final RestClient customerClient;
    private final ProxyGateway proxy;

    public CustomerProxyController(
            @Qualifier("customerRestClient") RestClient customerClient, ProxyGateway proxy) {
        this.customerClient = customerClient;
        this.proxy = proxy;
    }

    @PostMapping("/register")
    public ResponseEntity<JsonNode> register(@RequestBody JsonNode body) {
        return proxy.post(customerClient, "/api/customers/register", body, null);
    }

    @PostMapping("/login")
    public ResponseEntity<JsonNode> login(@RequestBody JsonNode body) {
        return proxy.post(customerClient, "/api/customers/login", body, null);
    }

    @GetMapping("/me")
    public ResponseEntity<JsonNode> me(HttpServletRequest request) {
        return proxy.get(customerClient, "/api/customers/me", identityHeaders(request));
    }

    @PutMapping("/me")
    public ResponseEntity<JsonNode> updateMe(HttpServletRequest request, @RequestBody JsonNode body) {
        return proxy.put(customerClient, "/api/customers/me", body, identityHeaders(request));
    }

    @GetMapping("/me/addresses")
    public ResponseEntity<JsonNode> listAddresses(HttpServletRequest request) {
        return proxy.get(customerClient, "/api/customers/me/addresses", identityHeaders(request));
    }

    @PostMapping("/me/addresses")
    public ResponseEntity<JsonNode> createAddress(HttpServletRequest request, @RequestBody JsonNode body) {
        return proxy.post(customerClient, "/api/customers/me/addresses", body, identityHeaders(request));
    }

    @GetMapping("/me/addresses/{id}")
    public ResponseEntity<JsonNode> getAddress(HttpServletRequest request, @PathVariable String id) {
        return proxy.get(customerClient, "/api/customers/me/addresses/" + id, identityHeaders(request));
    }

    @PutMapping("/me/addresses/{id}")
    public ResponseEntity<JsonNode> updateAddress(
            HttpServletRequest request, @PathVariable String id, @RequestBody JsonNode body) {
        return proxy.put(customerClient, "/api/customers/me/addresses/" + id, body, identityHeaders(request));
    }

    @DeleteMapping("/me/addresses/{id}")
    public ResponseEntity<Void> deleteAddress(HttpServletRequest request, @PathVariable String id) {
        return proxy.delete(customerClient, "/api/customers/me/addresses/" + id, identityHeaders(request));
    }

    @PutMapping("/me/addresses/{id}/default")
    public ResponseEntity<JsonNode> setDefault(HttpServletRequest request, @PathVariable String id) {
        return proxy.put(customerClient, "/api/customers/me/addresses/" + id + "/default", null, identityHeaders(request));
    }

    private static HttpHeaders identityHeaders(HttpServletRequest request) {
        HttpHeaders headers = new HttpHeaders();
        Object token = request.getAttribute(CustomerJwtFilter.CUSTOMER_TOKEN_ATTR);
        if (token == null) {
            String header = request.getHeader("Authorization");
            if (header != null && !header.isBlank()) {
                headers.add(HttpHeaders.AUTHORIZATION, header);
            }
        } else {
            headers.add(HttpHeaders.AUTHORIZATION, "Bearer " + token);
        }
        Object customerId = request.getAttribute(CustomerJwtFilter.CUSTOMER_ID_ATTR);
        if (customerId != null) {
            headers.add("X-Commerce-Customer-Id", customerId.toString());
        }
        Object email = request.getAttribute("commerce.email");
        if (email != null) {
            headers.add("X-Commerce-Email", email.toString());
        }
        Object displayName = request.getAttribute("commerce.displayName");
        if (displayName != null) {
            headers.add("X-Commerce-Display-Name", displayName.toString());
        }
        return headers;
    }
}
