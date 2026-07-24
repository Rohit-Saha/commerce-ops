package com.commerceops.gateway.web;

import com.commerceops.common.web.RawResponse;
import com.commerceops.gateway.service.ProxyGateway;
import com.commerceops.gateway.web.dto.CatalogItemResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.beans.factory.annotation.Qualifier;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestClient;
import org.springframework.web.server.ResponseStatusException;
import org.springframework.web.util.UriComponentsBuilder;
import org.springframework.web.util.UriUtils;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;

/**
 * Storefront-facing catalog over the catalog-service projection + search.
 */
@RestController
@RequestMapping("/api/store")
public class StoreCatalogController {

    private final RestClient catalogClient;
    private final ProxyGateway proxy;

    public StoreCatalogController(
            @Qualifier("catalogRestClient") RestClient catalogClient, ProxyGateway proxy) {
        this.catalogClient = catalogClient;
        this.proxy = proxy;
    }

    @GetMapping("/catalog")
    public List<CatalogItemResponse> catalog() {
        ResponseEntity<JsonNode> response = proxy.get(catalogClient, "/api/catalog");
        JsonNode body = ApiBodies.data(response.getBody());
        if (body == null || !body.isArray()) {
            return List.of();
        }
        List<CatalogItemResponse> items = new ArrayList<>();
        for (JsonNode node : body) {
            items.add(toCatalogItem(node));
        }
        return items;
    }

    @GetMapping("/catalog/search")
    public ResponseEntity<JsonNode> search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        String uri = UriComponentsBuilder.fromPath("/api/catalog/search")
                .queryParam("q", q)
                .queryParam("category", category)
                .queryParam("tag", tag)
                .queryParam("inStock", inStock)
                .queryParam("page", page)
                .queryParam("size", size)
                .build(true)
                .toUriString();
        return proxy.get(catalogClient, uri);
    }

    @GetMapping("/catalog/categories")
    public ResponseEntity<JsonNode> categories() {
        return proxy.get(catalogClient, "/api/catalog/categories");
    }

    @GetMapping("/catalog/by-slug/{slug}")
    public CatalogItemResponse bySlug(@PathVariable String slug) {
        ResponseEntity<JsonNode> response = proxy.get(
                catalogClient, "/api/catalog/by-slug/" + UriUtils.encodePathSegment(slug, StandardCharsets.UTF_8));
        JsonNode body = ApiBodies.data(response.getBody());
        if (body == null || body.isNull()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That product isn’t available.");
        }
        return toCatalogItem(body);
    }

    @GetMapping("/catalog/{sku}")
    public CatalogItemResponse catalogItem(@PathVariable String sku) {
        ResponseEntity<JsonNode> response = proxy.get(
                catalogClient, "/api/catalog/" + UriUtils.encodePathSegment(sku, StandardCharsets.UTF_8));
        JsonNode body = ApiBodies.data(response.getBody());
        if (body == null || body.isNull()) {
            throw new ResponseStatusException(HttpStatus.NOT_FOUND, "That product isn’t available.");
        }
        return toCatalogItem(body);
    }

    private static CatalogItemResponse toCatalogItem(JsonNode node) {
        String sku = text(node, "sku");
        String displayTitle = text(node, "displayTitle");
        String inventoryName = text(node, "inventoryName");
        String name = !displayTitle.isBlank() ? displayTitle : (!inventoryName.isBlank() ? inventoryName : text(node, "name"));
        BigDecimal unitPrice = decimal(node, "unitPrice");
        int availableQty = node.path("availableQty").asInt(0);
        boolean inStock = node.has("inStock") ? node.path("inStock").asBoolean(availableQty > 0) : availableQty > 0;
        return new CatalogItemResponse(
                sku,
                name,
                displayTitle.isBlank() ? name : displayTitle,
                text(node, "slug"),
                text(node, "shortDescription"),
                text(node, "bodyText"),
                text(node, "categorySlug"),
                text(node, "categoryName"),
                stringList(node.get("tags")),
                stringList(node.get("imageUrls")),
                text(node, "primaryImageUrl"),
                unitPrice,
                availableQty,
                inStock);
    }

    private static List<String> stringList(JsonNode node) {
        if (node == null || !node.isArray()) {
            return List.of();
        }
        List<String> values = new ArrayList<>();
        node.forEach(n -> values.add(n.asText()));
        return values;
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
