package com.commerceops.saga;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {
        "com.commerceops.saga",
        "com.commerceops.common.kafka",
        "com.commerceops.common.idempotency"
})
@EntityScan(basePackages = {
        "com.commerceops.saga",
        "com.commerceops.common.kafka",
        "com.commerceops.common.idempotency"
})
@EnableJpaRepositories(basePackages = {
        "com.commerceops.saga",
        "com.commerceops.common.kafka",
        "com.commerceops.common.idempotency"
})
public class SagaOrchestratorApplication {

    public static void main(String[] args) {
        SpringApplication.run(SagaOrchestratorApplication.class, args);
    }
}
