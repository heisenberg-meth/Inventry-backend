package com.ims.tenant.service;

import com.ims.dto.TransferOrderStatusRequest;
import com.ims.model.StockMovement;
import com.ims.model.TransferOrder;
import com.ims.product.Product;
import com.ims.product.ProductService;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.InsufficientStockException;
import com.ims.tenant.domain.warehouse.WarehouseProduct;
import com.ims.platform.service.TenantService;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.tenant.repository.TransferOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockTransactionService {

    private final StockMovementRepository stockMovementRepository;
    private final TenantService tenantService;
    private final WarehouseProductRepository warehouseProductRepository;
    private final TransferOrderRepository transferOrderRepository;
    private final com.ims.product.ProductRepository productRepository;
    private final ProductService productService;

    private void checkWarehouseType() {
        Long tenantId = TenantContext.getTenantId();
        if (tenantId == null) {
            throw new com.ims.shared.exception.TenantContextException("No tenant context found");
        }
        if (!tenantService.isWarehouse(tenantId)) {
            throw new IllegalArgumentException("Only available for WAREHOUSE tenants");
        }
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "stock", key = "'id:' + #productId", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "products", key = "'id:' + #productId", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "products", key = "'list'", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "reports", key = "'stock-report'", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "dashboard", key = "'dashboard'", cacheResolver = "tenantAwareCacheResolver")
    })
    public void stockInInternal(@NonNull Long productId, int qty, String notes, @NonNull Long userId) {
        Product product = productService
            .findByIdWithLock(productId)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        int previousStock = product.getStock();
        product.setStock(previousStock + qty);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        stockMovementRepository.save(
            StockMovement.builder()
                .productId(productId)
                .movementType("IN")
                .quantity(qty)
                .previousStock(previousStock)
                .newStock(product.getStock())
                .notes(notes)
                .createdBy(userId)
                .build());

        log.info(
            "Stock IN Attempt: product={} qty={} {}→{}",
            productId,
            qty,
            previousStock,
            product.getStock());
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "stock", key = "'id:' + #productId", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "products", key = "'id:' + #productId", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "products", key = "'list'", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "reports", key = "'stock-report'", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "dashboard", key = "'dashboard'", cacheResolver = "tenantAwareCacheResolver")
    })
    public void stockOutInternal(@NonNull Long productId, int qty, String notes, @NonNull Long userId) {
        Product product = productService
            .findByIdWithLock(productId)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        if (product.getStock() < qty) {
            throw new InsufficientStockException(
                "Insufficient stock. Requested: " + qty + ", Available: " + product.getStock(),
                product.getStock(),
                qty);
        }

        int previousStock = product.getStock();
        product.setStock(previousStock - qty);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        stockMovementRepository.save(
            StockMovement.builder()
                .productId(productId)
                .movementType("OUT")
                .quantity(qty)
                .previousStock(previousStock)
                .newStock(product.getStock())
                .notes(notes)
                .createdBy(userId)
                .build());

        log.info(
            "Stock OUT Attempt: product={} qty={} {}→{}",
            productId,
            qty,
            previousStock,
            product.getStock());
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "stock", key = "'id:' + #productId", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "products", key = "'id:' + #productId", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "products", key = "'list'", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "reports", key = "'stock-report'", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "dashboard", key = "'dashboard'", cacheResolver = "tenantAwareCacheResolver")
    })
    public void stockAdjustInternal(@NonNull Long productId, int qty, String notes, @NonNull Long userId) {
        Product product = productRepository
            .findById(productId)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        int previousStock = product.getStock();
        int newStock = previousStock + qty;
        
        if (newStock < 0) {
            throw new InsufficientStockException(
                "Adjustment would result in negative stock. Current: " + previousStock + ", Adjustment: " + qty,
                previousStock,
                Math.abs(qty));
        }

        product.setStock(newStock);
        product.setUpdatedAt(LocalDateTime.now());
        productRepository.save(product);

        stockMovementRepository.save(
            StockMovement.builder()
                .productId(productId)
                .movementType("ADJUSTMENT")
                .quantity(qty)
                .previousStock(previousStock)
                .newStock(product.getStock())
                .notes(notes)
                .createdBy(userId)
                .build());

        log.info(
            "Stock ADJUST: product={} qty={} {}→{}",
            productId,
            qty,
            previousStock,
            product.getStock());
    }

    @Transactional
    @Caching(evict = {
        @CacheEvict(value = "stock", key = "'order:' + #id", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "stock", key = "'location:' + #request.toLocation", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "products", key = "'list'", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "reports", key = "'stock-report'", cacheResolver = "tenantAwareCacheResolver"),
        @CacheEvict(value = "dashboard", key = "'dashboard'", cacheResolver = "tenantAwareCacheResolver")
    })
    public @NonNull TransferOrder updateTransferStatus(@NonNull Long id, @NonNull TransferOrderStatusRequest request,
            @NonNull Long userId) {
        checkWarehouseType();
        TransferOrder order = transferOrderRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Transfer Order not found"));

        String currentStatus = order.getStatus();
        String newStatus = request.getStatus();

        if ("COMPLETED".equals(currentStatus)) {
            throw new IllegalArgumentException("Already COMPLETED");
        }

        if ("PENDING".equals(currentStatus) && !"IN_TRANSIT".equals(newStatus)) {
            throw new IllegalArgumentException("PENDING can only transition to IN_TRANSIT");
        }

        if ("IN_TRANSIT".equals(currentStatus) && !"COMPLETED".equals(newStatus)) {
            throw new IllegalArgumentException("IN_TRANSIT can only transition to COMPLETED");
        }

        order.setStatus(newStatus);
        order = transferOrderRepository.save(order);

        if ("COMPLETED".equals(newStatus)) {
            WarehouseProduct wp = warehouseProductRepository
                .findById(Objects.requireNonNull(order.getProductId()))
                .orElseThrow(() -> new EntityNotFoundException("Warehouse product not found"));
            wp.setStorageLocation(order.getToLocation());
            warehouseProductRepository.save(wp);

            stockMovementRepository.save(
                StockMovement.builder()
                    .productId(order.getProductId())
                    .movementType("TRANSFER")
                    .quantity(order.getQuantity())
                    .notes("Transfer from " + order.getFromLocation() + " to " + order.getToLocation())
                    .createdBy(userId)
                    .referenceId(order.getId())
                    .referenceType("TRANSFER_ORDER")
                    .build());

            log.info(
                "Transfer order COMPLETED: id={} product={} quantity={} {} -> {}",
                order.getId(),
                order.getProductId(),
                order.getQuantity(),
                order.getFromLocation(),
                order.getToLocation());
        }

        return order;
    }
}
