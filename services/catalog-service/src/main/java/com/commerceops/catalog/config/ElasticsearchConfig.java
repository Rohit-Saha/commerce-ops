package com.commerceops.catalog.config;

import co.elastic.clients.elasticsearch.ElasticsearchClient;
import co.elastic.clients.json.jackson.JacksonJsonpMapper;
import co.elastic.clients.transport.rest_client.RestClientTransport;
import org.apache.http.HttpHost;
import org.elasticsearch.client.RestClient;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

@Configuration
public class ElasticsearchConfig {

    @Bean(destroyMethod = "close")
    public RestClient elasticsearchLowLevelClient(CatalogProperties properties) {
        String url = properties.elasticsearchUrl() != null
                ? properties.elasticsearchUrl()
                : "http://localhost:9200";
        return RestClient.builder(HttpHost.create(url)).build();
    }

    @Bean
    public ElasticsearchClient elasticsearchClient(RestClient elasticsearchLowLevelClient) {
        return new ElasticsearchClient(
                new RestClientTransport(elasticsearchLowLevelClient, new JacksonJsonpMapper()));
    }
}
