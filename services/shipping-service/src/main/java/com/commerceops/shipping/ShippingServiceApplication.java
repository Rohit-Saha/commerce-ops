package com.commerceops.shipping;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.commerceops.shipping",
        "com.commerceops.common.kafka",
        "com.commerceops.common.idempotency"
})
@EnableScheduling
@EntityScan({
        "com.commerceops.shipping",
        "com.commerceops.common.kafka",
        "com.commerceops.common.idempotency"
})
@EnableJpaRepositories({
        "com.commerceops.shipping",
        "com.commerceops.common.kafka",
        "com.commerceops.common.idempotency"
})
public class ShippingServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(ShippingServiceApplication.class, args);
    }
}
