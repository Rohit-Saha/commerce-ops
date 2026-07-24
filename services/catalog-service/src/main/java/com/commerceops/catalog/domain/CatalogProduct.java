package com.commerceops.catalog.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.math.BigDecimal;
import java.time.Instant;

@Entity
@Table(name = "catalog_products")
public class CatalogProduct {

    @Id
    @Column(length = 64)
    private String sku;

    @Column(nullable = false, length = 128)
    private String name;

    @Column(name = "unit_price", nullable = false, precision = 12, scale = 2)
    private BigDecimal unitPrice;

    @Column(name = "available_qty", nullable = false)
    private int availableQty;

    @Column(nullable = false)
    private boolean deleted;

    @Column(name = "updated_at", nullable = false)
    private Instant updatedAt;

    @Column(name = "display_title", length = 256)
    private String displayTitle;

    @Column(length = 256)
    private String slug;

    @Column(name = "short_description")
    private String shortDescription;

    @Column(name = "body_text")
    private String bodyText;

    @Column(name = "category_slug", length = 128)
    private String categorySlug;

    @Column(name = "category_name", length = 128)
    private String categoryName;

    @Column(name = "tags_json")
    private String tagsJson;

    @Column(name = "image_urls_json")
    private String imageUrlsJson;

    @Column(name = "primary_image_url", length = 1024)
    private String primaryImageUrl;

    @Column(name = "cms_published", nullable = false)
    private boolean cmsPublished;

    @Column(name = "cms_published_at")
    private Instant cmsPublishedAt;

    @Column(name = "cms_updated_at")
    private Instant cmsUpdatedAt;

    @Column(name = "strapi_document_id", length = 64)
    private String strapiDocumentId;

    public String getSku() { return sku; }
    public void setSku(String sku) { this.sku = sku; }
    public String getName() { return name; }
    public void setName(String name) { this.name = name; }
    public BigDecimal getUnitPrice() { return unitPrice; }
    public void setUnitPrice(BigDecimal unitPrice) { this.unitPrice = unitPrice; }
    public int getAvailableQty() { return availableQty; }
    public void setAvailableQty(int availableQty) { this.availableQty = availableQty; }
    public boolean isDeleted() { return deleted; }
    public void setDeleted(boolean deleted) { this.deleted = deleted; }
    public Instant getUpdatedAt() { return updatedAt; }
    public void setUpdatedAt(Instant updatedAt) { this.updatedAt = updatedAt; }
    public String getDisplayTitle() { return displayTitle; }
    public void setDisplayTitle(String displayTitle) { this.displayTitle = displayTitle; }
    public String getSlug() { return slug; }
    public void setSlug(String slug) { this.slug = slug; }
    public String getShortDescription() { return shortDescription; }
    public void setShortDescription(String shortDescription) { this.shortDescription = shortDescription; }
    public String getBodyText() { return bodyText; }
    public void setBodyText(String bodyText) { this.bodyText = bodyText; }
    public String getCategorySlug() { return categorySlug; }
    public void setCategorySlug(String categorySlug) { this.categorySlug = categorySlug; }
    public String getCategoryName() { return categoryName; }
    public void setCategoryName(String categoryName) { this.categoryName = categoryName; }
    public String getTagsJson() { return tagsJson; }
    public void setTagsJson(String tagsJson) { this.tagsJson = tagsJson; }
    public String getImageUrlsJson() { return imageUrlsJson; }
    public void setImageUrlsJson(String imageUrlsJson) { this.imageUrlsJson = imageUrlsJson; }
    public String getPrimaryImageUrl() { return primaryImageUrl; }
    public void setPrimaryImageUrl(String primaryImageUrl) { this.primaryImageUrl = primaryImageUrl; }
    public boolean isCmsPublished() { return cmsPublished; }
    public void setCmsPublished(boolean cmsPublished) { this.cmsPublished = cmsPublished; }
    public Instant getCmsPublishedAt() { return cmsPublishedAt; }
    public void setCmsPublishedAt(Instant cmsPublishedAt) { this.cmsPublishedAt = cmsPublishedAt; }
    public Instant getCmsUpdatedAt() { return cmsUpdatedAt; }
    public void setCmsUpdatedAt(Instant cmsUpdatedAt) { this.cmsUpdatedAt = cmsUpdatedAt; }
    public String getStrapiDocumentId() { return strapiDocumentId; }
    public void setStrapiDocumentId(String strapiDocumentId) { this.strapiDocumentId = strapiDocumentId; }

    public String storefrontTitle() {
        return displayTitle != null && !displayTitle.isBlank() ? displayTitle : name;
    }
}
