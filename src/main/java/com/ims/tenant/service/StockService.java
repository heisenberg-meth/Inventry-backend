package com.ims.tenant.service;

import com.ims.dto.TransferOrderStatusRequest;
import com.ims.model.Product;
import com.ims.model.StockMovement;
import com.ims.model.Tenant;
import com.ims.model.TransferOrder;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.InsufficientStockException;
import com.ims.tenant.domain.warehouse.WarehouseProduct;
import com.ims.tenant.repository.ProductRepository;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.tenant.repository.TransferOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

  private final ProductRepository productRepository;
  private final StockMovementRepository stockMovementRepository;
  private final TenantRepository tenantRepository;
  private final WarehouseProductRepository warehouseProductRepository;
  private final TransferOrderRepository transferOrderRepository;

  private void checkWarehouseType(Long tenantId) {
    Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
    if (!"WAREHOUSE".equals(tenant.getBusinessType())) {
      throw new IllegalArgumentException("Only available for WAREHOUSE tenants");
    }
  }

  public Page<WarehouseProduct> getProductsByLocation(String location, Pageable pageable) {
    Long tenantId = TenantContext.get();
    checkWarehouseType(tenantId);
    return warehouseProductRepository.findByTenantIdAndLocation(tenantId, location, pageable);
  }

  public Page<TransferOrder> getTransferOrders(Pageable pageable) {
    Long tenantId = TenantContext.get();
    checkWarehouseType(tenantId);
    return transferOrderRepository.findByTenantId(tenantId, pageable);
  }

  public TransferOrder getTransferOrderById(Long id) {
    Long tenantId = TenantContext.get();
    checkWarehouseType(tenantId);
    return transferOrderRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Transfer Order not found"));
  }

  @Transactional
  public TransferOrder updateTransferStatus(Long id, TransferOrderStatusRequest request) {
    Long tenantId = TenantContext.get();
    checkWarehouseType(tenantId);
    TransferOrder order =
        transferOrderRepository
            .findByIdAndTenantId(id, tenantId)
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
    return transferOrderRepository.save(order);
  }

  @Transactional
  @CacheEvict(
      value = {"stock", "products"},
      allEntries = true)
  public void stockIn(Long tenantId, Long productId, int qty, String notes, Long userId) {
    Product product =
        productRepository
            .findByIdAndTenantId(productId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    int previousStock = product.getStock();
    product.setStock(previousStock + qty);
    product.setUpdatedAt(LocalDateTime.now());
    productRepository.save(product);

    stockMovementRepository.save(
        StockMovement.builder()
            .tenantId(tenantId)
            .productId(productId)
            .movementType("IN")
            .quantity(qty)
            .previousStock(previousStock)
            .newStock(product.getStock())
            .notes(notes)
            .createdBy(userId)
            .build());

    log.info(
        "Stock IN: tenant={} product={} qty={} {}→{}",
        tenantId,
        productId,
        qty,
        previousStock,
        product.getStock());
  }

  @Transactional
  @CacheEvict(
      value = {"stock", "products"},
      allEntries = true)
  public void stockOut(Long tenantId, Long productId, int qty, String notes, Long userId) {
    // Pessimistic write lock prevents concurrent oversell
    Product product =
        productRepository
            .findByIdAndTenantIdWithLock(productId, tenantId)
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
            .tenantId(tenantId)
            .productId(productId)
            .movementType("OUT")
            .quantity(qty)
            .previousStock(previousStock)
            .newStock(product.getStock())
            .notes(notes)
            .createdBy(userId)
            .build());

    log.info(
        "Stock OUT: tenant={} product={} qty={} {}→{}",
        tenantId,
        productId,
        qty,
        previousStock,
        product.getStock());
  }

  @Transactional
  @CacheEvict(
      value = {"stock", "products"},
      allEntries = true)
  public void stockAdjust(Long tenantId, Long productId, int qty, String notes, Long userId) {
    Product product =
        productRepository
            .findByIdAndTenantIdWithLock(productId, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    int previousStock = product.getStock();
    product.setStock(previousStock + qty); // qty can be negative for adjustment
    product.setUpdatedAt(LocalDateTime.now());
    productRepository.save(product);

    stockMovementRepository.save(
        StockMovement.builder()
            .tenantId(tenantId)
            .productId(productId)
            .movementType("ADJUSTMENT")
            .quantity(qty)
            .previousStock(previousStock)
            .newStock(product.getStock())
            .notes(notes)
            .createdBy(userId)
            .build());

    log.info(
        "Stock ADJUST: tenant={} product={} qty={} {}→{}",
        tenantId,
        productId,
        qty,
        previousStock,
        product.getStock());
  }

  public Page<StockMovement> getMovements(Pageable pageable) {
    Long tenantId = TenantContext.get();
    return stockMovementRepository.findByTenantIdOrderByCreatedAtDesc(tenantId, pageable);
  }

  public Page<StockMovement> getFilteredMovements(
      Long productId, LocalDateTime from, LocalDateTime to, Pageable pageable) {
    Long tenantId = TenantContext.get();
    return stockMovementRepository.findByFilters(tenantId, productId, from, to, pageable);
  }
}
