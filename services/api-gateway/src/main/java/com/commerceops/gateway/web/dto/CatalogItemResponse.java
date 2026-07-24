package com.commerceops.gateway.web.dto;

import java.math.BigDecimal;
import java.util.List;

public record CatalogItemResponse(
        String sku,
        String name,
        String displayTitle,
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
        boolean inStock
) {
}
