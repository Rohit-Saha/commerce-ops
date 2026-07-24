package com.commerceops.order;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@ComponentScan(basePackages = {"com.commerceops.order", "com.commerceops.common"})
@EntityScan(basePackages = {
        "com.commerceops.order.domain",
        "com.commerceops.common.kafka",
        "com.commerceops.common.idempotency"
})
@EnableJpaRepositories(basePackages = {
        "com.commerceops.order.repository",
        "com.commerceops.common.kafka",
        "com.commerceops.common.idempotency"
})
public class OrderServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(OrderServiceApplication.class, args);
    }
}
