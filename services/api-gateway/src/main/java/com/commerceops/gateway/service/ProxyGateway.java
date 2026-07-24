package com.commerceops.gateway.service;

import com.commerceops.gateway.resilience.DownstreamCircuitNames;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.http.HttpHeaders;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.time.Instant;
import java.util.Set;
import java.util.function.Supplier;

/**
 * Thin wrapper around {@link RestClient} used by every proxy controller. Downstream
 * status is preserved; JSON bodies are normalized to the shared success/error envelope
 * when the peer has not already wrapped them. Hop-by-hop headers are stripped so Tomcat
 * does not double-apply {@code Transfer-Encoding: chunked}.
 * <p>
 * Each call runs through a per-downstream Resilience4j {@link CircuitBreaker}.
 */
@Component
public class ProxyGateway {

    private static final Set<String> HOP_BY_HOP = Set.of(
            "connection",
            "keep-alive",
            "proxy-authenticate",
            "proxy-authorization",
            "te",
            "trailers",
            "transfer-encoding",
            "upgrade",
            "content-length"
    );

    private final ObjectMapper objectMapper;
    private final CircuitBreakerRegistry circuitBreakerRegistry;
    private final DownstreamCircuitNames circuitNames;

    public ProxyGateway(
            ObjectMapper objectMapper,
            CircuitBreakerRegistry circuitBreakerRegistry,
            DownstreamCircuitNames circuitNames) {
        this.objectMapper = objectMapper;
        this.circuitBreakerRegistry = circuitBreakerRegistry;
        this.circuitNames = circuitNames;
    }

    public ResponseEntity<JsonNode> get(RestClient client, String uri) {
        return get(client, uri, null);
    }

    public ResponseEntity<JsonNode> get(RestClient client, String uri, HttpHeaders forwardHeaders) {
        return decorate(client, () -> {
            RestClient.RequestHeadersSpec<?> spec = client.get().uri(uri);
            if (forwardHeaders != null && !forwardHeaders.isEmpty()) {
                spec = spec.headers(headers -> headers.addAll(forwardHeaders));
            }
            return sanitize(spec.retrieve().toEntity(JsonNode.class));
        });
    }

    public ResponseEntity<JsonNode> post(RestClient client, String uri, Object body, HttpHeaders forwardHeaders) {
        return decorate(client, () -> {
            RestClient.RequestBodySpec spec = client.post().uri(uri);
            if (forwardHeaders != null && !forwardHeaders.isEmpty()) {
                spec.headers(headers -> headers.addAll(forwardHeaders));
            }
            ResponseEntity<JsonNode> response = body != null
                    ? spec.body(body).retrieve().toEntity(JsonNode.class)
                    : spec.retrieve().toEntity(JsonNode.class);
            return sanitize(response);
        });
    }

    public ResponseEntity<JsonNode> put(RestClient client, String uri, Object body, HttpHeaders forwardHeaders) {
        return decorate(client, () -> {
            RestClient.RequestBodySpec spec = client.put().uri(uri);
            if (forwardHeaders != null && !forwardHeaders.isEmpty()) {
                spec.headers(headers -> headers.addAll(forwardHeaders));
            }
            ResponseEntity<JsonNode> response = body != null
                    ? spec.body(body).retrieve().toEntity(JsonNode.class)
                    : spec.retrieve().toEntity(JsonNode.class);
            return sanitize(response);
        });
    }

    public ResponseEntity<Void> delete(RestClient client, String uri) {
        return delete(client, uri, null);
    }

    public ResponseEntity<Void> delete(RestClient client, String uri, HttpHeaders forwardHeaders) {
        return decorate(client, () -> {
            RestClient.RequestHeadersSpec<?> spec = client.delete().uri(uri);
            if (forwardHeaders != null && !forwardHeaders.isEmpty()) {
                spec = spec.headers(headers -> headers.addAll(forwardHeaders));
            }
            ResponseEntity<Void> downstream = spec.retrieve().toBodilessEntity();
            HttpHeaders headers = new HttpHeaders();
            downstream.getHeaders().forEach((name, values) -> {
                if (name != null && !HOP_BY_HOP.contains(name.toLowerCase())) {
                    headers.put(name, values);
                }
            });
            return ResponseEntity.status(downstream.getStatusCode()).headers(headers).build();
        });
    }

    private <T> T decorate(RestClient client, Supplier<T> supplier) {
        String name = circuitNames.nameOf(client);
        CircuitBreaker breaker = circuitBreakerRegistry.circuitBreaker(name);
        return CircuitBreaker.decorateSupplier(breaker, supplier).get();
    }

    private ResponseEntity<JsonNode> sanitize(ResponseEntity<JsonNode> downstream) {
        HttpHeaders headers = new HttpHeaders();
        downstream.getHeaders().forEach((name, values) -> {
            if (name != null && !HOP_BY_HOP.contains(name.toLowerCase())) {
                headers.put(name, values);
            }
        });
        JsonNode body = ensureEnvelope(downstream.getBody(), downstream.getStatusCode().value());
        return ResponseEntity.status(downstream.getStatusCode()).headers(headers).body(body);
    }

    /**
     * If downstream already returned {@code { success: ... }}, pass through.
     * Otherwise wrap the payload as {@code { success, message, data, meta }}.
     */
    JsonNode ensureEnvelope(JsonNode body, int status) {
        if (body == null) {
            return null;
        }
        if (body.isObject() && body.has("success") && body.get("success").isBoolean()) {
            return body;
        }
        ObjectNode envelope = objectMapper.createObjectNode();
        boolean ok = status >= 200 && status < 300;
        envelope.put("success", ok);
        envelope.put("message", ok ? (status == 201 ? "Created" : "OK") : "Request failed");
        if (!ok) {
            envelope.put("status", status);
            envelope.put("error", httpReason(status));
            envelope.put("timestamp", Instant.now().toString());
        }
        envelope.set("data", body);
        ObjectNode meta = envelope.putObject("meta");
        meta.put("timestamp", Instant.now().toString());
        return envelope;
    }

    private static String httpReason(int status) {
        return switch (status) {
            case 400 -> "Bad Request";
            case 401 -> "Unauthorized";
            case 403 -> "Forbidden";
            case 404 -> "Not Found";
            case 409 -> "Conflict";
            case 429 -> "Too Many Requests";
            case 502 -> "Bad Gateway";
            case 503 -> "Service Unavailable";
            default -> status >= 500 ? "Internal Server Error" : "Error";
        };
    }
}
