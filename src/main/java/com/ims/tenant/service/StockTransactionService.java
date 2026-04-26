package com.ims.tenant.service;

import com.ims.dto.TransferOrderStatusRequest;
import com.ims.model.StockMovement;
import com.ims.model.TransferOrder;
import com.ims.model.TransferOrderStatus;
import com.ims.platform.service.TenantService;
import com.ims.product.Product;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.InsufficientStockException;
import com.ims.tenant.domain.warehouse.WarehouseProduct;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.tenant.repository.TransferOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Caching;
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
    public void stockInInternal(
            Long productId, int qty, String notes, Long userId) {
        if (qty <= 0)
            throw new IllegalArgumentException("Quantity must be positive");

        int updated = productRepository.incrementStock(productId, qty, Objects.requireNonNull(LocalDateTime.now()));
        if (updated == 0) {
            throw new EntityNotFoundException("Product not found: " + productId);
        }

        Product product = productRepository
                .findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        int newStock = product.getStock();
        int previousStock = newStock - qty;

        StockMovement tmpMovement = StockMovement.builder()
                .productId(productId)
                .movementType("IN")
                .quantity(qty)
                .previousStock(previousStock)
                .newStock(newStock)
                .notes(notes)
                .createdBy(userId)
                .build();
        stockMovementRepository.save(Objects.requireNonNull(tmpMovement));

        log.info("Stock IN: product={} qty={} {}→{}", productId, qty, previousStock, newStock);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "stock", key = "'id:' + #productId", cacheResolver = "tenantAwareCacheResolver"),
            @CacheEvict(value = "products", key = "'id:' + #productId", cacheResolver = "tenantAwareCacheResolver"),
            @CacheEvict(value = "products", key = "'list'", cacheResolver = "tenantAwareCacheResolver"),
            @CacheEvict(value = "reports", key = "'stock-report'", cacheResolver = "tenantAwareCacheResolver"),
            @CacheEvict(value = "dashboard", key = "'dashboard'", cacheResolver = "tenantAwareCacheResolver")
    })
    public void stockOutInternal(
            Long productId, int qty, String notes, Long userId) {
        if (qty <= 0)
            throw new IllegalArgumentException("Quantity must be positive");

        int updated = productRepository.decrementStockIfAvailable(productId, qty, Objects.requireNonNull(LocalDateTime.now()));
        if (updated == 0) {
            // Either product not found or insufficient stock
            Product product = Objects.requireNonNull(productRepository
                    .findById(productId)
                    .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId)));
            throw new InsufficientStockException(
                    "Insufficient stock. Requested: " + qty + ", Available: " + product.getStock(),
                    product.getStock(),
                    qty);
        }

        Product product = productRepository
                .findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        int newStock = product.getStock();
        int previousStock = newStock + qty;

        StockMovement tmpMovement = StockMovement.builder()
                .productId(productId)
                .movementType("OUT")
                .quantity(qty)
                .previousStock(previousStock)
                .newStock(newStock)
                .notes(notes)
                .createdBy(userId)
                .build();
        stockMovementRepository.save(Objects.requireNonNull(tmpMovement));

        log.info("Stock OUT: product={} qty={} {}→{}", productId, qty, previousStock, newStock);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "stock", key = "'id:' + #productId", cacheResolver = "tenantAwareCacheResolver"),
            @CacheEvict(value = "products", key = "'id:' + #productId", cacheResolver = "tenantAwareCacheResolver"),
            @CacheEvict(value = "products", key = "'list'", cacheResolver = "tenantAwareCacheResolver"),
            @CacheEvict(value = "reports", key = "'stock-report'", cacheResolver = "tenantAwareCacheResolver"),
            @CacheEvict(value = "dashboard", key = "'dashboard'", cacheResolver = "tenantAwareCacheResolver")
    })
    public void stockAdjustInternal(
            Long productId, int qty, String notes, Long userId) {
        if (qty == 0)
            return;

        int updated = productRepository.adjustStockIfValid(productId, qty, Objects.requireNonNull(LocalDateTime.now()));
        if (updated == 0) {
            Product product = Objects.requireNonNull(productRepository
                    .findById(productId)
                    .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId)));
            throw new InsufficientStockException(
                    "Adjustment would result in negative stock. Current: "
                            + product.getStock()
                            + ", Adjustment: "
                            + qty,
                    product.getStock(),
                    Math.abs(qty));
        }

        Product product = productRepository
                .findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Product not found"));

        int newStock = product.getStock();
        int previousStock = newStock - qty;

        StockMovement tmpMovement = StockMovement.builder()
                .productId(productId)
                .movementType("ADJUSTMENT")
                .quantity(qty)
                .previousStock(previousStock)
                .newStock(newStock)
                .notes(notes)
                .createdBy(userId)
                .build();
        stockMovementRepository.save(Objects.requireNonNull(tmpMovement));

        log.info("Stock ADJUST: product={} qty={} {}→{}", productId, qty, previousStock, newStock);
    }

    @Transactional
    @Caching(evict = {
            @CacheEvict(value = "stock", key = "'order:' + #id", cacheResolver = "tenantAwareCacheResolver"),
            @CacheEvict(value = "stock", key = "'location:' + #request.toLocation", cacheResolver = "tenantAwareCacheResolver"),
            @CacheEvict(value = "products", key = "'list'", cacheResolver = "tenantAwareCacheResolver"),
            @CacheEvict(value = "reports", key = "'stock-report'", cacheResolver = "tenantAwareCacheResolver"),
            @CacheEvict(value = "dashboard", key = "'dashboard'", cacheResolver = "tenantAwareCacheResolver")
    })
    public TransferOrder updateTransferStatus(
            Long id, TransferOrderStatusRequest request, Long userId) {
        checkWarehouseType();
        TransferOrder order = Objects.requireNonNull(
                transferOrderRepository
                        .findById(id)
                        .orElseThrow(() -> new EntityNotFoundException("Transfer Order not found")));

        TransferOrderStatus currentStatus = order.getStatus();
        TransferOrderStatus newStatus = TransferOrderStatus.valueOf(Objects.requireNonNull(request.getStatus()));

        if (TransferOrderStatus.COMPLETED == currentStatus) {
            throw new IllegalArgumentException("Already COMPLETED");
        }

        if (TransferOrderStatus.PENDING == currentStatus && TransferOrderStatus.IN_TRANSIT != newStatus) {
            throw new IllegalArgumentException("PENDING can only transition to IN_TRANSIT");
        }

        if (TransferOrderStatus.IN_TRANSIT == currentStatus && TransferOrderStatus.COMPLETED != newStatus) {
            throw new IllegalArgumentException("IN_TRANSIT can only transition to COMPLETED");
        }

        order.setStatus(newStatus);
        TransferOrder savedOrder = transferOrderRepository.save(order);
        order = Objects.requireNonNull(savedOrder);

        if (TransferOrderStatus.COMPLETED == newStatus) {
            WarehouseProduct tmpWp = warehouseProductRepository
                    .findById(Objects.requireNonNull(order.getProductId()))
                    .orElseThrow(() -> new EntityNotFoundException("Warehouse product not found"));
            WarehouseProduct wp = Objects.requireNonNull(tmpWp);
            wp.setStorageLocation(order.getToLocation());
            warehouseProductRepository.save(wp);

            StockMovement tmpMovement = StockMovement.builder()
                    .productId(order.getProductId())
                    .movementType("TRANSFER")
                    .quantity(order.getQuantity())
                    .notes("Transfer from " + order.getFromLocation() + " to " + order.getToLocation())
                    .createdBy(userId)
                    .referenceId(order.getId())
                    .referenceType("TRANSFER_ORDER")
                    .build();
            stockMovementRepository.save(Objects.requireNonNull(tmpMovement));

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
