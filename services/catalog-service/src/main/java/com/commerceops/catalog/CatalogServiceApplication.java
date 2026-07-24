package com.commerceops.catalog;

import com.commerceops.catalog.config.CatalogProperties;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.autoconfigure.domain.EntityScan;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.ComponentScan;
import org.springframework.data.jpa.repository.config.EnableJpaRepositories;
import org.springframework.scheduling.annotation.EnableScheduling;

@SpringBootApplication
@EnableScheduling
@EnableConfigurationProperties(CatalogProperties.class)
@ComponentScan(basePackages = {"com.commerceops.catalog", "com.commerceops.common"})
@EntityScan(basePackages = {
        "com.commerceops.catalog.domain",
        "com.commerceops.common.idempotency"
})
@EnableJpaRepositories(basePackages = {
        "com.commerceops.catalog.repository",
        "com.commerceops.common.idempotency"
})
public class CatalogServiceApplication {

    public static void main(String[] args) {
        SpringApplication.run(CatalogServiceApplication.class, args);
    }
}
