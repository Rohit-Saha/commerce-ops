package com.commerceops.gateway.web;

import com.commerceops.common.web.ApiError;
import com.commerceops.common.web.RawResponse;

import com.commerceops.gateway.service.ProxyGateway;
import com.commerceops.gateway.web.filter.ApiKeyAuthFilter;
import com.commerceops.gateway.web.filter.CustomerJwtFilter;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.servlet.http.HttpServletRequest;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;
import org.springframework.web.util.UriUtils;

import java.nio.charset.StandardCharsets;

@RestController
@RequestMapping("/api/invoices")
public class InvoiceProxyController {

    private final RestClient invoiceClient;
    private final RestClient orderClient;
    private final ProxyGateway proxy;
    private final ObjectMapper objectMapper;

    public InvoiceProxyController(
            @Qualifier("invoiceRestClient") RestClient invoiceClient,
            @Qualifier("orderRestClient") RestClient orderClient,
            ProxyGateway proxy,
            ObjectMapper objectMapper) {
        this.invoiceClient = invoiceClient;
        this.orderClient = orderClient;
        this.proxy = proxy;
        this.objectMapper = objectMapper;
    }

    @GetMapping
    public ResponseEntity<JsonNode> list() {
        return proxy.get(invoiceClient, "/api/invoices");
    }

    @GetMapping("/by-order/{orderId}")
    public ResponseEntity<?> byOrder(HttpServletRequest request, @PathVariable String orderId) {
        ResponseEntity<JsonNode> response = proxy.get(
                invoiceClient, "/api/invoices/by-order/" + encode(orderId));
        return enforceStorefrontOwnership(request, response);
    }

    @GetMapping("/{id}")
    public ResponseEntity<?> get(HttpServletRequest request, @PathVariable String id) {
        ResponseEntity<JsonNode> response = proxy.get(invoiceClient, "/api/invoices/" + encode(id));
        return enforceStorefrontOwnership(request, response);
    }

    @RawResponse
    @GetMapping("/{id}/pdf")
    public ResponseEntity<?> pdf(HttpServletRequest request, @PathVariable String id) {
        ResponseEntity<JsonNode> meta = proxy.get(invoiceClient, "/api/invoices/" + encode(id));
        ResponseEntity<?> ownership = enforceStorefrontOwnership(request, meta);
        if (ownership.getStatusCode().isError() || ownership.getBody() instanceof ApiError) {
            return ownership;
        }

        try {
            ResponseEntity<byte[]> pdf = invoiceClient.get()
                    .uri("/api/invoices/{id}/pdf", id)
                    .retrieve()
                    .toEntity(byte[].class);
            HttpHeaders headers = new HttpHeaders();
            MediaType contentType = pdf.getHeaders().getContentType();
            headers.setContentType(contentType != null ? contentType : MediaType.APPLICATION_PDF);
            String disposition = pdf.getHeaders().getFirst(HttpHeaders.CONTENT_DISPOSITION);
            if (disposition != null) {
                headers.set(HttpHeaders.CONTENT_DISPOSITION, disposition);
            } else {
                headers.set(HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=\"invoice-" + id + ".pdf\"");
            }
            return ResponseEntity.status(pdf.getStatusCode()).headers(headers).body(pdf.getBody());
        } catch (RestClientResponseException ex) {
            return ResponseEntity.status(ex.getStatusCode())
                    .contentType(MediaType.APPLICATION_JSON)
                    .body(parseError(ex));
        }
    }

    private ResponseEntity<?> enforceStorefrontOwnership(
            HttpServletRequest request, ResponseEntity<JsonNode> response) {
        Object role = request.getAttribute(ApiKeyAuthFilter.API_KEY_ROLE_ATTR);
        if (role != ApiKeyAuthFilter.ApiKeyRole.STOREFRONT) {
            return response;
        }
        JsonNode body = ApiBodies.data(response.getBody());
        if (body == null || !body.hasNonNull("orderId") || !body.hasNonNull("customerId")) {
            return response;
        }
        String customerId = (String) request.getAttribute(CustomerJwtFilter.CUSTOMER_ID_ATTR);
        if (customerId != null && customerId.equals(body.get("customerId").asText())) {
            return response;
        }
        // Cross-check order ownership if invoice customerId mismatched somehow
        String orderId = body.get("orderId").asText();
        try {
            ResponseEntity<JsonNode> order = proxy.get(orderClient, "/api/orders/" + encode(orderId));
            JsonNode orderBody = ApiBodies.data(order.getBody());
            if (orderBody != null && orderBody.hasNonNull("customerId")
                    && customerId.equals(orderBody.get("customerId").asText())) {
                return response;
            }
        } catch (RestClientResponseException ignored) {
            // fall through to 404
        }
        return ResponseEntity.status(HttpStatus.NOT_FOUND)
                .body(ApiError.of(HttpStatus.NOT_FOUND.value(), "Not Found", "We couldn’t find that invoice."));
    }

    private JsonNode parseError(RestClientResponseException ex) {
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

    private static String encode(String value) {
        return UriUtils.encodePathSegment(value, StandardCharsets.UTF_8);
    }
}
