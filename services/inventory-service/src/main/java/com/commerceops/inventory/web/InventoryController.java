package com.commerceops.inventory.web;

import com.commerceops.inventory.service.InventoryService;
import com.commerceops.inventory.web.dto.CreateStockItemRequest;
import com.commerceops.inventory.web.dto.StockItemResponse;
import com.commerceops.inventory.web.dto.UpdateStockItemRequest;
import jakarta.validation.Valid;
import org.springframework.http.HttpStatus;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseStatus;
import org.springframework.web.bind.annotation.RestController;

import java.util.List;

@RestController
@RequestMapping("/api/inventory")
public class InventoryController {

    private final InventoryService inventoryService;

    public InventoryController(InventoryService inventoryService) {
        this.inventoryService = inventoryService;
    }

    @GetMapping
    public List<StockItemResponse> listStock() {
        return inventoryService.listStock();
    }

    @GetMapping("/{sku}")
    public StockItemResponse getStock(@PathVariable String sku) {
        return inventoryService.getStock(sku);
    }

    @PostMapping
    @ResponseStatus(HttpStatus.CREATED)
    public StockItemResponse createStock(@Valid @RequestBody CreateStockItemRequest request) {
        return inventoryService.createStock(request);
    }

    @PutMapping("/{sku}")
    public StockItemResponse updateStock(
            @PathVariable String sku, @Valid @RequestBody UpdateStockItemRequest request) {
        return inventoryService.updateStock(sku, request);
    }

    @DeleteMapping("/{sku}")
    @ResponseStatus(HttpStatus.NO_CONTENT)
    public void softDeleteStock(@PathVariable String sku) {
        inventoryService.softDeleteStock(sku);
    }

    @PostMapping("/{sku}/restock")
    public StockItemResponse restock(@PathVariable String sku, @RequestParam int qty) {
        return inventoryService.restock(sku, qty);
    }
}
