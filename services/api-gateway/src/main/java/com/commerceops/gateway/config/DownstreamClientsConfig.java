package com.commerceops.gateway.config;

import com.commerceops.gateway.resilience.DownstreamCircuitNames;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

/**
 * One {@link RestClient} per downstream service, each pre-bound to its base URL from
 * {@link GatewayProperties}. Bean names double as {@code @Qualifier} values for injection.
 * Each client is registered with a Resilience4j circuit-breaker name.
 */
@Configuration
public class DownstreamClientsConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    public RestClient orderRestClient(GatewayProperties properties, DownstreamCircuitNames circuits) {
        return register(circuits, "order", properties.services().order());
    }

    @Bean
    public RestClient inventoryRestClient(GatewayProperties properties, DownstreamCircuitNames circuits) {
        return register(circuits, "inventory", properties.services().inventory());
    }

    @Bean
    public RestClient paymentRestClient(GatewayProperties properties, DownstreamCircuitNames circuits) {
        return register(circuits, "payment", properties.services().payment());
    }

    @Bean
    public RestClient shippingRestClient(GatewayProperties properties, DownstreamCircuitNames circuits) {
        return register(circuits, "shipping", properties.services().shipping());
    }

    @Bean
    public RestClient sagaRestClient(GatewayProperties properties, DownstreamCircuitNames circuits) {
        return register(circuits, "saga", properties.services().saga());
    }

    @Bean
    public RestClient customerRestClient(GatewayProperties properties, DownstreamCircuitNames circuits) {
        return register(circuits, "customer", properties.services().customer());
    }

    @Bean
    public RestClient catalogRestClient(GatewayProperties properties, DownstreamCircuitNames circuits) {
        return register(circuits, "catalog", properties.services().catalog());
    }

    @Bean
    public RestClient invoiceRestClient(GatewayProperties properties, DownstreamCircuitNames circuits) {
        return register(circuits, "invoice", properties.services().invoice());
    }

    private static RestClient register(DownstreamCircuitNames circuits, String name, String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        RestClient client = RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
        circuits.register(client, name);
        return client;
    }
}
