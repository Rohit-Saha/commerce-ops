package com.commerceops.gateway.resilience;

import org.springframework.stereotype.Component;
import org.springframework.web.client.RestClient;

import java.util.IdentityHashMap;
import java.util.Map;

/**
 * Maps each downstream {@link RestClient} bean to a Resilience4j circuit-breaker instance name.
 */
@Component
public class DownstreamCircuitNames {

    private final Map<RestClient, String> names = new IdentityHashMap<>();

    public synchronized void register(RestClient client, String circuitName) {
        names.put(client, circuitName);
    }

    public synchronized String nameOf(RestClient client) {
        String name = names.get(client);
        if (name == null) {
            throw new IllegalStateException("RestClient is not registered with a circuit-breaker name");
        }
        return name;
    }
}
