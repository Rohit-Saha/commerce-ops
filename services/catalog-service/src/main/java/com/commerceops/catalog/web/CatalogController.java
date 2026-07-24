package com.commerceops.catalog.web;

import com.commerceops.catalog.service.CatalogProjectionService;
import com.commerceops.catalog.web.dto.CatalogCategoryResponse;
import com.commerceops.catalog.web.dto.CatalogProductResponse;
import com.commerceops.catalog.web.dto.CatalogSearchResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/catalog")
public class CatalogController {

    private final CatalogProjectionService projectionService;

    public CatalogController(CatalogProjectionService projectionService) {
        this.projectionService = projectionService;
    }

    @GetMapping
    public List<CatalogProductResponse> list() {
        return projectionService.listPublished();
    }

    @GetMapping("/search")
    public CatalogSearchResponse search(
            @RequestParam(required = false) String q,
            @RequestParam(required = false) String category,
            @RequestParam(required = false) String tag,
            @RequestParam(required = false) Boolean inStock,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "24") int size) {
        return projectionService.search(q, category, tag, inStock, page, size);
    }

    @GetMapping("/categories")
    public List<CatalogCategoryResponse> categories() {
        return projectionService.listCategories();
    }

    @GetMapping("/by-slug/{slug}")
    public CatalogProductResponse bySlug(@PathVariable String slug) {
        return projectionService.getPublishedBySlug(slug);
    }

    @GetMapping("/{sku}")
    public CatalogProductResponse get(@PathVariable String sku) {
        return projectionService.getPublishedBySku(sku);
    }
}
