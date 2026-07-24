package com.commerceops.catalog.repository;

import com.commerceops.catalog.domain.CatalogProduct;
import org.springframework.data.jpa.repository.JpaRepository;

import java.util.List;
import java.util.Optional;

public interface CatalogProductRepository extends JpaRepository<CatalogProduct, String> {

    List<CatalogProduct> findByDeletedFalseAndCmsPublishedTrueOrderBySkuAsc();

    Optional<CatalogProduct> findBySkuAndDeletedFalseAndCmsPublishedTrue(String sku);

    Optional<CatalogProduct> findBySlugAndDeletedFalseAndCmsPublishedTrue(String slug);

    List<CatalogProduct> findByDeletedFalseOrderBySkuAsc();

    Optional<CatalogProduct> findBySkuAndDeletedFalse(String sku);

    List<CatalogProduct> findByDeletedFalseAndCmsPublishedTrue();
}
