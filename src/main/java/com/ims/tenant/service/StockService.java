package com.ims.tenant.service;

import com.ims.model.Product;
import com.ims.model.StockMovement;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.InsufficientStockException;
import com.ims.tenant.repository.ProductRepository;
import com.ims.tenant.repository.StockMovementRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.LocalDateTime;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;

    @Transactional
    @CacheEvict(value = {"stock", "products"}, allEntries = true)
    public void stockIn(Long tenantId, Long productId, int qty, String notes, Long userId) {
        Product product = productRepository.findByIdAndTenantId(productId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        int previousStock = product.getStock();
        product.setStock(previousStock + qty);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        stockMovementRepository.save(StockMovement.builder()
                .tenantId(tenantId)
                .productId(productId)
                .movementType("IN")
                .quantity(qty)
                .previousStock(previousStock)
                .newStock(product.getStock())
                .notes(notes)
                .createdBy(userId)
                .build());

        log.info("Stock IN: tenant={} product={} qty={} {}→{}", tenantId, productId, qty, previousStock, product.getStock());
    }

    @Transactional
    @CacheEvict(value = {"stock", "products"}, allEntries = true)
    public void stockOut(Long tenantId, Long productId, int qty, String notes, Long userId) {
        // Pessimistic write lock prevents concurrent oversell
        Product product = productRepository.findByIdAndTenantIdWithLock(productId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        if (product.getStock() < qty) {
            throw new InsufficientStockException(
                    "Insufficient stock. Requested: " + qty + ", Available: " + product.getStock(),
                    product.getStock(), qty);
        }

        int previousStock = product.getStock();
        product.setStock(previousStock - qty);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        stockMovementRepository.save(StockMovement.builder()
                .tenantId(tenantId)
                .productId(productId)
                .movementType("OUT")
                .quantity(qty)
                .previousStock(previousStock)
                .newStock(product.getStock())
                .notes(notes)
                .createdBy(userId)
                .build());

        log.info("Stock OUT: tenant={} product={} qty={} {}→{}", tenantId, productId, qty, previousStock, product.getStock());
    }

    @Transactional
    @CacheEvict(value = {"stock", "products"}, allEntries = true)
    public void stockAdjust(Long tenantId, Long productId, int qty, String notes, Long userId) {
        Product product = productRepository.findByIdAndTenantIdWithLock(productId, tenantId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        int previousStock = product.getStock();
        product.setStock(previousStock + qty); // qty can be negative for adjustment
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        stockMovementRepository.save(StockMovement.builder()
                .tenantId(tenantId)
                .productId(productId)
                .movementType("ADJUSTMENT")
                .quantity(qty)
                .previousStock(previousStock)
                .newStock(product.getStock())
                .notes(notes)
                .createdBy(userId)
                .build());

        log.info("Stock ADJUST: tenant={} product={} qty={} {}→{}", tenantId, productId, qty, previousStock, product.getStock());
    }

    public Page<StockMovement> getMovements(Pageable pageable) {
        Long tenantId = TenantContext.get();
        return stockMovementRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
    }

    public Page<StockMovement> getFilteredMovements(Long productId, LocalDateTime from, LocalDateTime to, Pageable pageable) {
        Long tenantId = TenantContext.get();
        return stockMovementRepository.findByFilters(tenantId, productId, from, to, pageable);
    }
}
