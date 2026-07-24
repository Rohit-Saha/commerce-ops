package com.commerceops.payment;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication(scanBasePackages = {
        "com.commerceops.payment",
        "com.commerceops.common.kafka",
        "com.commerceops.common.idempotency"
})
@EnableScheduling
@EntityScan({
        "com.commerceops.payment",
        "com.commerceops.common.kafka",
        "com.commerceops.common.idempotency"
})
@EnableJpaRepositories({
        "com.commerceops.payment",
        "com.commerceops.common.kafka",
        "com.commerceops.common.idempotency"
})
public class PaymentServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(PaymentServiceApplication.class, args);
    }
}
