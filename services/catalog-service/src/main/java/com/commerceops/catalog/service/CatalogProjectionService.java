package com.commerceops.catalog.service;

import com.commerceops.catalog.domain.CatalogProduct;
import com.commerceops.catalog.repository.CatalogProductRepository;
import com.commerceops.catalog.search.CatalogDocument;
import com.commerceops.catalog.search.CatalogSearchIndexService;
import com.commerceops.catalog.strapi.StrapiSyncService;
import com.commerceops.catalog.web.dto.CatalogCategoryResponse;
import com.commerceops.catalog.web.dto.CatalogProductResponse;
import com.commerceops.catalog.web.dto.CatalogSearchResponse;
import com.commerceops.common.events.Payloads;
import com.fasterxml.jackson.core.type.TypeReference;
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

import java.math.BigDecimal;
import java.time.Instant;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

@Service
public class CatalogProjectionService {

    private static final Logger log = LoggerFactory.getLogger(CatalogProjectionService.class);

    private final CatalogProductRepository productRepository;
    private final RestClient inventoryRestClient;
    private final CircuitBreaker inventoryCircuit;
    private final CatalogSearchIndexService indexService;
    private final StrapiSyncService strapiSyncService;
    private final ObjectMapper objectMapper;

    public CatalogProjectionService(
            CatalogProductRepository productRepository,
            @Qualifier("inventoryRestClient") RestClient inventoryRestClient,
            CircuitBreakerRegistry circuitBreakerRegistry,
            CatalogSearchIndexService indexService,
            StrapiSyncService strapiSyncService,
            ObjectMapper objectMapper) {
        this.productRepository = productRepository;
        this.inventoryRestClient = inventoryRestClient;
        this.inventoryCircuit = circuitBreakerRegistry.circuitBreaker("inventory");
        this.indexService = indexService;
        this.strapiSyncService = strapiSyncService;
        this.objectMapper = objectMapper;
    }

    @Transactional(readOnly = true)
    public List<CatalogProductResponse> listPublished() {
        return productRepository.findByDeletedFalseAndCmsPublishedTrueOrderBySkuAsc().stream()
                .map(this::toResponse)
                .toList();
    }

    @Transactional(readOnly = true)
    public CatalogProductResponse getPublishedBySku(String sku) {
        return productRepository.findBySkuAndDeletedFalseAndCmsPublishedTrue(sku)
                .map(this::toResponse)
                .orElseThrow(() -> new CatalogProductNotFoundException(sku));
    }

    @Transactional(readOnly = true)
    public CatalogProductResponse getPublishedBySlug(String slug) {
        return productRepository.findBySlugAndDeletedFalseAndCmsPublishedTrue(slug)
                .map(this::toResponse)
                .orElseThrow(() -> new CatalogProductNotFoundException(slug));
    }

    @Transactional(readOnly = true)
    public List<CatalogCategoryResponse> listCategories() {
        Map<String, CatalogCategoryResponse> map = new LinkedHashMap<>();
        for (CatalogProduct product : productRepository.findByDeletedFalseAndCmsPublishedTrueOrderBySkuAsc()) {
            if (product.getCategorySlug() == null || product.getCategorySlug().isBlank()) {
                continue;
            }
            map.putIfAbsent(
                    product.getCategorySlug(),
                    new CatalogCategoryResponse(product.getCategorySlug(), product.getCategoryName(), 0));
            CatalogCategoryResponse existing = map.get(product.getCategorySlug());
            map.put(product.getCategorySlug(),
                    new CatalogCategoryResponse(existing.slug(), existing.name(), existing.count() + 1));
        }
        return new ArrayList<>(map.values());
    }

    public CatalogSearchResponse search(String q, String category, String tag, Boolean inStock, int page, int size) {
        CatalogSearchIndexService.SearchResult result =
                indexService.search(q, category, tag, inStock, page, size);
        List<CatalogProductResponse> items = result.items().stream().map(this::fromDocument).toList();
        List<CatalogSearchResponse.FacetBucket> categories = result.facets().categories().stream()
                .map(b -> new CatalogSearchResponse.FacetBucket(b.value(), b.count()))
                .toList();
        List<CatalogSearchResponse.FacetBucket> tags = result.facets().tags().stream()
                .map(b -> new CatalogSearchResponse.FacetBucket(b.value(), b.count()))
                .toList();
        return new CatalogSearchResponse(
                items,
                new CatalogSearchResponse.Facets(categories, tags),
                result.total());
    }

    @Transactional
    public void applyStockItemChanged(Payloads.StockItemChanged change) {
        CatalogProduct product = productRepository.findById(change.sku()).orElseGet(CatalogProduct::new);
        boolean isNew = product.getSku() == null;
        product.setSku(change.sku());
        product.setName(change.name());
        product.setUnitPrice(change.unitPrice());
        product.setAvailableQty(Math.max(0, change.availableQty()));
        product.setDeleted(change.deleted());
        product.setUpdatedAt(Instant.now());
        if (isNew) {
            product.setCmsPublished(false);
        }
        productRepository.save(product);

        if (change.deleted()) {
            indexService.deleteProduct(change.sku());
        } else {
            indexService.indexProduct(product);
            if (!product.isCmsPublished()) {
                strapiSyncService.syncSku(change.sku());
            }
        }
        log.debug("Projected sku={} available={} deleted={}", change.sku(), change.availableQty(), change.deleted());
    }

    @Transactional
    public void bootstrapFromInventoryIfEmpty() {
        if (!productRepository.findByDeletedFalseOrderBySkuAsc().isEmpty()) {
            return;
        }
        log.info("Catalog has no active products — cold-starting from inventory");
        RestClientException lastError = null;
        for (int attempt = 1; attempt <= 15; attempt++) {
            try {
                JsonNode body = CircuitBreaker.decorateSupplier(inventoryCircuit, () ->
                        inventoryRestClient.get()
                                .uri("/api/inventory")
                                .retrieve()
                                .body(JsonNode.class)).get();
                if (body == null || !body.isArray()) {
                    log.warn("Inventory returned empty/non-array body during cold start");
                    return;
                }
                Instant now = Instant.now();
                int upserted = 0;
                for (JsonNode node : body) {
                    String sku = text(node, "sku");
                    if (sku.isBlank()) {
                        continue;
                    }
                    CatalogProduct product = new CatalogProduct();
                    product.setSku(sku);
                    product.setName(text(node, "name"));
                    product.setUnitPrice(decimal(node, "unitPrice"));
                    product.setAvailableQty(Math.max(0, node.path("availableQty").asInt(0)));
                    product.setDeleted(false);
                    product.setCmsPublished(false);
                    product.setUpdatedAt(now);
                    productRepository.save(product);
                    upserted++;
                }
                log.info("Cold-start upserted {} products from inventory", upserted);
                return;
            } catch (CallNotPermittedException | RestClientException ex) {
                lastError = ex instanceof RestClientException rce ? rce : new RestClientException(ex.getMessage(), ex);
                log.info("Cold-start attempt {}/15 failed: {}", attempt, ex.getMessage());
                try {
                    Thread.sleep(2000L);
                } catch (InterruptedException ie) {
                    Thread.currentThread().interrupt();
                    return;
                }
                if (!productRepository.findByDeletedFalseOrderBySkuAsc().isEmpty()) {
                    return;
                }
            }
        }
        log.warn("Cold-start pull from inventory failed after retries (will rely on Kafka): {}",
                lastError != null ? lastError.getMessage() : "unknown");
    }

    private CatalogProductResponse toResponse(CatalogProduct product) {
        return new CatalogProductResponse(
                product.getSku(),
                product.storefrontTitle(),
                product.getName(),
                product.getSlug(),
                product.getShortDescription(),
                product.getBodyText(),
                product.getCategorySlug(),
                product.getCategoryName(),
                readList(product.getTagsJson()),
                readList(product.getImageUrlsJson()),
                product.getPrimaryImageUrl(),
                product.getUnitPrice(),
                product.getAvailableQty(),
                product.getAvailableQty() > 0,
                product.isCmsPublished());
    }

    private CatalogProductResponse fromDocument(CatalogDocument doc) {
        return new CatalogProductResponse(
                doc.sku(),
                doc.displayTitle(),
                doc.inventoryName(),
                doc.slug(),
                doc.shortDescription(),
                doc.bodyText(),
                doc.categorySlug(),
                doc.categoryName(),
                doc.tags() == null ? List.of() : doc.tags(),
                doc.imageUrls() == null ? List.of() : doc.imageUrls(),
                doc.primaryImageUrl(),
                doc.unitPrice(),
                doc.availableQty(),
                doc.inStock(),
                true);
    }

    private List<String> readList(String json) {
        if (json == null || json.isBlank()) {
            return List.of();
        }
        try {
            return objectMapper.readValue(json, new TypeReference<>() {});
        } catch (Exception ex) {
            return List.of();
        }
    }

    private static String text(JsonNode node, String field) {
        JsonNode value = node.get(field);
        return value == null || value.isNull() ? "" : value.asText();
    }

    private static BigDecimal decimal(JsonNode node, String field) {
        JsonNode value = node.get(field);
        if (value == null || value.isNull()) {
            return BigDecimal.ZERO;
        }
        if (value.isNumber()) {
            return value.decimalValue();
        }
        return new BigDecimal(value.asText("0"));
    }
}
