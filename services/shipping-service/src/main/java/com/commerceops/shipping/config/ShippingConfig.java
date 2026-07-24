package com.commerceops.shipping.config;

import com.commerceops.shipping.carrier.ShippingProvider;
import com.commerceops.shipping.carrier.ShiprocketShippingProvider;
import com.commerceops.shipping.carrier.SimulatedShippingProvider;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.http.client.JdkClientHttpRequestFactory;
import org.springframework.web.client.RestClient;

import java.net.http.HttpClient;
import java.time.Duration;

@Configuration
@EnableConfigurationProperties(ShippingProperties.class)
public class ShippingConfig {

    private static final Duration CONNECT_TIMEOUT = Duration.ofSeconds(2);
    private static final Duration READ_TIMEOUT = Duration.ofSeconds(5);

    @Bean
    RestClient orderRestClient(ShippingProperties properties) {
        return timedClient(properties.getOrderService().getBaseUrl());
    }

    @Bean
    RestClient sagaRestClient(ShippingProperties properties) {
        return timedClient(properties.getSagaService().getBaseUrl());
    }

    @Bean
    ShippingProvider shippingProvider(
            ShippingProperties properties,
            SimulatedShippingProvider simulated,
            ShiprocketShippingProvider shiprocket) {
        return properties.isShiprocket() ? shiprocket : simulated;
    }

    private static RestClient timedClient(String baseUrl) {
        HttpClient httpClient = HttpClient.newBuilder()
                .connectTimeout(CONNECT_TIMEOUT)
                .build();
        JdkClientHttpRequestFactory factory = new JdkClientHttpRequestFactory(httpClient);
        factory.setReadTimeout(READ_TIMEOUT);
        return RestClient.builder()
                .baseUrl(baseUrl)
                .requestFactory(factory)
                .build();
    }
}
