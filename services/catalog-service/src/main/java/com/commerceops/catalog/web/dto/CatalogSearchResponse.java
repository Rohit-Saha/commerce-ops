package com.commerceops.catalog.web.dto;

import java.util.List;

public record CatalogSearchResponse(
        List<CatalogProductResponse> items,
        Facets facets,
        long total
) {
    public record Facets(List<FacetBucket> categories, List<FacetBucket> tags) {}
    public record FacetBucket(String value, long count) {}
}
