package com.commerceops.gateway.service;

import com.commerceops.gateway.resilience.DownstreamCircuitNames;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.junit.jupiter.api.Test;
import org.springframework.web.client.RestClient;

import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verifyNoInteractions;

class ProxyGatewayCircuitBreakerTest {

    @Test
    void openCircuitShortCircuitsWithoutCallingRestClient() {
        CircuitBreakerRegistry registry = CircuitBreakerRegistry.ofDefaults();
        CircuitBreaker breaker = registry.circuitBreaker("order");
        breaker.transitionToOpenState();

        DownstreamCircuitNames names = new DownstreamCircuitNames();
        RestClient client = mock(RestClient.class);
        names.register(client, "order");

        ProxyGateway gateway = new ProxyGateway(new ObjectMapper(), registry, names);

        assertThrows(CallNotPermittedException.class, () -> gateway.get(client, "/api/orders"));
        verifyNoInteractions(client);
    }
}
