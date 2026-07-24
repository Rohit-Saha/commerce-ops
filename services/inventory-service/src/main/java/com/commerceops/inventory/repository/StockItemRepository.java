package com.commerceops.inventory.repository;

import com.commerceops.inventory.domain.StockItem;
import jakarta.persistence.LockModeType;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Lock;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

import java.util.List;
import java.util.Optional;

public interface StockItemRepository extends JpaRepository<StockItem, String> {

    List<StockItem> findByDeletedAtIsNull();

    Optional<StockItem> findBySkuAndDeletedAtIsNull(String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockItem s where s.sku = :sku")
    Optional<StockItem> findWithLockBySku(@Param("sku") String sku);

    @Lock(LockModeType.PESSIMISTIC_WRITE)
    @Query("select s from StockItem s where s.sku = :sku and s.deletedAt is null")
    Optional<StockItem> findActiveWithLockBySku(@Param("sku") String sku);
}
