package com.commerceops.catalog.web;

import com.commerceops.catalog.config.CatalogProperties;
import com.commerceops.catalog.strapi.StrapiSyncService;
import com.commerceops.common.web.RawResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestHeader;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RawResponse
@RestController
@RequestMapping("/api/catalog/internal")
public class StrapiWebhookController {

    private final StrapiSyncService strapiSyncService;
    private final CatalogProperties properties;

    public StrapiWebhookController(StrapiSyncService strapiSyncService, CatalogProperties properties) {
        this.strapiSyncService = strapiSyncService;
        this.properties = properties;
    }

    @PostMapping("/strapi-hook")
    public ResponseEntity<Void> onStrapiEvent(
            @RequestHeader(value = "X-Webhook-Secret", required = false) String secret,
            @RequestBody JsonNode body) {
        if (properties.strapiWebhookSecret() == null
                || !properties.strapiWebhookSecret().equals(secret)) {
            return ResponseEntity.status(HttpStatus.UNAUTHORIZED).build();
        }
        String event = body.path("event").asText("");
        String sku = body.path("sku").asText("");
        if (sku.isBlank()) {
            return ResponseEntity.badRequest().build();
        }
        if (event.contains("delete") || event.contains("unpublish")) {
            strapiSyncService.clearCms(sku);
        } else {
            strapiSyncService.syncSku(sku);
        }
        return ResponseEntity.accepted().build();
    }
}
