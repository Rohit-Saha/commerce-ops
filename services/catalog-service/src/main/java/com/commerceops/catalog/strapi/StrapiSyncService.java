package com.commerceops.catalog.strapi;

import com.commerceops.catalog.config.CatalogProperties;
import com.commerceops.catalog.domain.CatalogProduct;
import com.commerceops.catalog.repository.CatalogProductRepository;
import com.commerceops.catalog.search.CatalogSearchIndexService;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import io.github.resilience4j.circuitbreaker.CallNotPermittedException;
import io.github.resilience4j.circuitbreaker.CircuitBreaker;
import io.github.resilience4j.circuitbreaker.CircuitBreakerRegistry;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.client.RestClient;
import org.springframework.web.client.RestClientException;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;

@Service
public class StrapiSyncService {

    private static final Logger log = LoggerFactory.getLogger(StrapiSyncService.class);

    private final RestClient strapiRestClient;
    private final CircuitBreaker strapiCircuit;
    private final CatalogProductRepository productRepository;
    private final CatalogSearchIndexService indexService;
    private final CatalogProperties properties;
    private final ObjectMapper objectMapper;

    public StrapiSyncService(
            @Qualifier("strapiRestClient") RestClient strapiRestClient,
            CircuitBreakerRegistry circuitBreakerRegistry,
            CatalogProductRepository productRepository,
            CatalogSearchIndexService indexService,
            CatalogProperties properties,
            ObjectMapper objectMapper) {
        this.strapiRestClient = strapiRestClient;
        this.strapiCircuit = circuitBreakerRegistry.circuitBreaker("strapi");
        this.productRepository = productRepository;
        this.indexService = indexService;
        this.properties = properties;
        this.objectMapper = objectMapper;
    }

    @Transactional
    public void syncAllPublished() {
        try {
            JsonNode root = CircuitBreaker.decorateSupplier(strapiCircuit, () ->
                    strapiRestClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/api/products")
                                    .queryParam("status", "published")
                                    .queryParam("populate[0]", "category")
                                    .queryParam("populate[1]", "gallery")
                                    .queryParam("pagination[pageSize]", "100")
                                    .build())
                            .retrieve()
                            .body(JsonNode.class)).get();
            if (root == null || !root.has("data") || !root.get("data").isArray()) {
                log.info("Strapi sync: no published products");
                return;
            }
            int count = 0;
            for (JsonNode item : root.get("data")) {
                applyStrapiProduct(item);
                count++;
            }
            log.info("Strapi sync applied {} published products", count);
        } catch (CallNotPermittedException | RestClientException ex) {
            log.warn("Strapi sync failed: {}", ex.getMessage());
        }
    }

    @Transactional
    public void syncSku(String sku) {
        if (sku == null || sku.isBlank()) {
            return;
        }
        try {
            JsonNode root = CircuitBreaker.decorateSupplier(strapiCircuit, () ->
                    strapiRestClient.get()
                            .uri(uriBuilder -> uriBuilder
                                    .path("/api/products")
                                    .queryParam("filters[sku][$eq]", sku)
                                    .queryParam("status", "published")
                                    .queryParam("populate[0]", "category")
                                    .queryParam("populate[1]", "gallery")
                                    .build())
                            .retrieve()
                            .body(JsonNode.class)).get();
            if (root == null || !root.has("data") || !root.get("data").isArray() || root.get("data").isEmpty()) {
                clearCms(sku);
                return;
            }
            applyStrapiProduct(root.get("data").get(0));
        } catch (CallNotPermittedException | RestClientException ex) {
            log.warn("Strapi sync for sku={} failed: {}", sku, ex.getMessage());
        }
    }

    @Transactional
    public void clearCms(String sku) {
        Optional<CatalogProduct> opt = productRepository.findById(sku);
        if (opt.isEmpty()) {
            indexService.deleteProduct(sku);
            return;
        }
        CatalogProduct product = opt.get();
        product.setDisplayTitle(null);
        product.setSlug(null);
        product.setShortDescription(null);
        product.setBodyText(null);
        product.setCategorySlug(null);
        product.setCategoryName(null);
        product.setTagsJson(null);
        product.setImageUrlsJson(null);
        product.setPrimaryImageUrl(null);
        product.setCmsPublished(false);
        product.setCmsPublishedAt(null);
        product.setCmsUpdatedAt(Instant.now());
        product.setStrapiDocumentId(null);
        productRepository.save(product);
        indexService.deleteProduct(sku);
    }

    private void applyStrapiProduct(JsonNode item) {
        // Strapi v5 document shape: { documentId, sku, ... } or { id, attributes: {...} }
        JsonNode attrs = item.has("attributes") ? item.get("attributes") : item;
        String sku = text(attrs, "sku");
        if (sku.isBlank()) {
            return;
        }
        CatalogProduct product = productRepository.findById(sku).orElse(null);
        if (product == null) {
            log.info("Ignoring Strapi product for unknown inventory sku={}", sku);
            return;
        }

        product.setDisplayTitle(text(attrs, "displayTitle"));
        product.setSlug(text(attrs, "slug"));
        product.setShortDescription(text(attrs, "shortDescription"));
        product.setBodyText(richTextToPlain(attrs.get("body")));
        applyCategory(product, attrs.get("category"));
        product.setTagsJson(toJsonArray(attrs.get("tags")));
        List<String> images = galleryUrls(attrs.get("gallery"));
        product.setImageUrlsJson(writeJson(images));
        product.setPrimaryImageUrl(images.isEmpty() ? null : images.get(0));
        product.setCmsPublished(true);
        product.setCmsPublishedAt(parseInstant(text(attrs, "publishedAt")));
        product.setCmsUpdatedAt(Instant.now());
        product.setStrapiDocumentId(text(item, "documentId"));
        if (product.getStrapiDocumentId().isBlank()) {
            product.setStrapiDocumentId(String.valueOf(item.path("id").asText("")));
        }
        productRepository.save(product);
        indexService.indexProduct(product);
    }

    private void applyCategory(CatalogProduct product, JsonNode categoryNode) {
        if (categoryNode == null || categoryNode.isNull()) {
            product.setCategorySlug(null);
            product.setCategoryName(null);
            return;
        }
        JsonNode data = categoryNode.has("data") ? categoryNode.get("data") : categoryNode;
        if (data == null || data.isNull()) {
            product.setCategorySlug(null);
            product.setCategoryName(null);
            return;
        }
        JsonNode attrs = data.has("attributes") ? data.get("attributes") : data;
        product.setCategoryName(text(attrs, "name"));
        product.setCategorySlug(text(attrs, "slug"));
    }

    private List<String> galleryUrls(JsonNode galleryNode) {
        List<String> urls = new ArrayList<>();
        if (galleryNode == null || galleryNode.isNull()) {
            return urls;
        }
        JsonNode data = galleryNode.has("data") ? galleryNode.get("data") : galleryNode;
        if (data == null || !data.isArray()) {
            return urls;
        }
        String publicBase = properties.publicStrapiUrl() != null
                ? properties.publicStrapiUrl().replaceAll("/$", "")
                : "http://localhost:1337";
        for (JsonNode media : data) {
            JsonNode attrs = media.has("attributes") ? media.get("attributes") : media;
            String url = text(attrs, "url");
            if (url.isBlank()) {
                continue;
            }
            if (url.startsWith("http")) {
                urls.add(url);
            } else {
                urls.add(publicBase + (url.startsWith("/") ? url : "/" + url));
            }
        }
        return urls;
    }

    private String richTextToPlain(JsonNode body) {
        if (body == null || body.isNull()) {
            return null;
        }
        if (body.isTextual()) {
            return body.asText();
        }
        return body.toString();
    }

    private String toJsonArray(JsonNode tagsNode) {
        try {
            if (tagsNode == null || tagsNode.isNull()) {
                return objectMapper.writeValueAsString(List.of());
            }
            if (tagsNode.isArray()) {
                List<String> tags = new ArrayList<>();
                tagsNode.forEach(n -> tags.add(n.asText().trim().toLowerCase()));
                return objectMapper.writeValueAsString(tags.stream().filter(t -> !t.isBlank()).toList());
            }
            if (tagsNode.isTextual()) {
                String raw = tagsNode.asText();
                List<String> tags = new ArrayList<>();
                for (String part : raw.split(",")) {
                    String t = part.trim().toLowerCase();
                    if (!t.isBlank()) {
                        tags.add(t);
                    }
                }
                return objectMapper.writeValueAsString(tags);
            }
            return objectMapper.writeValueAsString(List.of());
        } catch (Exception ex) {
            return "[]";
        }
    }

    private String writeJson(List<String> values) {
        try {
            return objectMapper.writeValueAsString(values);
        } catch (Exception ex) {
            return "[]";
        }
    }

    private static Instant parseInstant(String value) {
        if (value == null || value.isBlank()) {
            return Instant.now();
        }
        try {
            return Instant.parse(value);
        } catch (Exception ex) {
            return Instant.now();
        }
    }

    private static String text(JsonNode node, String field) {
        if (node == null) {
            return "";
        }
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }
}
