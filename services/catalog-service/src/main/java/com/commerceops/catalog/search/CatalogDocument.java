package com.commerceops.catalog.search;

import java.math.BigDecimal;
import java.util.List;

public record CatalogDocument(
        String sku,
        String displayTitle,
        String inventoryName,
        String slug,
        String shortDescription,
        String bodyText,
        String categorySlug,
        String categoryName,
        List<String> tags,
        List<String> imageUrls,
        String primaryImageUrl,
        BigDecimal unitPrice,
        int availableQty,
        boolean inStock,
        String publishedAt,
        String updatedAt
) {
}
