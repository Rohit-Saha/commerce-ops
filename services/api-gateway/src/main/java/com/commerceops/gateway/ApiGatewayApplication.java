package com.commerceops.gateway;

import com.commerceops.gateway.config.CustomerJwtProperties;
import com.commerceops.gateway.config.GatewayProperties;
import com.commerceops.gateway.config.SecurityProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableConfigurationProperties({GatewayProperties.class, CustomerJwtProperties.class, SecurityProperties.class})
@EnableScheduling
public class ApiGatewayApplication {

    public static void main(String[] args) {
        SpringApplication.run(ApiGatewayApplication.class, args);
    }
}
