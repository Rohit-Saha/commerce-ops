package com.commerceops.shipping.service;

import com.commerceops.common.web.BusinessException;
import com.fasterxml.jackson.databind.JsonNode;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Component;
import org.springframework.web.client.ResourceAccessException;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientResponseException;

@Component
public class SagaLookupClient {

    private final RestClient sagaRestClient;
    private final CircuitBreaker circuitBreaker;

    public SagaLookupClient(
            @Qualifier("sagaRestClient") RestClient sagaRestClient,
            CircuitBreakerRegistry circuitBreakerRegistry) {
        this.sagaRestClient = sagaRestClient;
        this.circuitBreaker = circuitBreakerRegistry.circuitBreaker("saga");
    }

    public SagaRef fetchByOrder(String orderId) {
        try {
            JsonNode body = CircuitBreaker.decorateSupplier(circuitBreaker, () ->
                    sagaRestClient.get()
                            .uri("/api/sagas/by-order/{orderId}", orderId)
                            .retrieve()
                            .body(JsonNode.class)).get();
            JsonNode data = unwrap(body);
            if (data == null || data.isMissingNode() || data.isNull()) {
                throw BusinessException.notFound("We couldn’t find a saga for that order.");
            }
            String id = data.has("id") ? data.get("id").asText() : null;
            String status = text(data, "status");
            if (id == null || id.isBlank()) {
                throw BusinessException.notFound("We couldn’t find a saga for that order.");
            }
            return new SagaRef(id, status);
        } catch (CallNotPermittedException ex) {
            throw BusinessException.serviceUnavailable(
                    "Saga service temporarily unavailable; try again shortly.");
        } catch (ResourceAccessException ex) {
            throw BusinessException.serviceUnavailable(
                    "Saga service temporarily unavailable; try again shortly.");
        } catch (RestClientResponseException ex) {
            if (ex.getStatusCode().value() == 404) {
                throw BusinessException.notFound("We couldn’t find a saga for that order.");
            }
            throw BusinessException.badRequest("Could not load saga for shipment booking.");
        }
    }

    private static JsonNode unwrap(JsonNode body) {
        if (body != null && body.isObject() && body.path("success").isBoolean() && body.has("data")) {
            return body.get("data");
        }
        return body;
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? null : value.asText(null);
    }

    public record SagaRef(String sagaId, String status) {}
}
