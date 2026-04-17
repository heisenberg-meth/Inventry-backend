package com.ims.tenant.service;

import com.ims.dto.TransferOrderStatusRequest;
import com.ims.product.Product;
import com.ims.model.StockMovement;
import com.ims.model.TransferOrder;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.InsufficientStockException;
import com.ims.tenant.domain.warehouse.WarehouseProduct;
import com.ims.product.ProductService;
import com.ims.platform.service.TenantService;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.tenant.repository.TransferOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class StockService {

  private final ProductService productService;
  private final StockMovementRepository stockMovementRepository;
  private final TenantService tenantService;
  private final WarehouseProductRepository warehouseProductRepository;
  private final TransferOrderRepository transferOrderRepository;
  private final com.ims.product.ProductRepository productRepository; // Kept for JPA operations if needed, but primarily
                                                                     // using productService

  private void checkWarehouseType() {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("No tenant context found");
    }
    if (!tenantService.isWarehouse(tenantId)) {
      throw new IllegalArgumentException("Only available for WAREHOUSE tenants");
    }
  }

  public @NonNull Page<WarehouseProduct> getProductsByLocation(@NonNull String location, @NonNull Pageable pageable) {
    checkWarehouseType();
    return Objects.requireNonNull(warehouseProductRepository.findByLocation(location, pageable));
  }

  public @NonNull Page<TransferOrder> getTransferOrders(@NonNull Pageable pageable) {
    checkWarehouseType();
    return Objects.requireNonNull(transferOrderRepository.findAll(pageable));
  }

  public @NonNull TransferOrder getTransferOrderById(@NonNull Long id) {
    checkWarehouseType();
    return Objects.requireNonNull(transferOrderRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Transfer Order not found")));
  }

  @Transactional
  @CacheEvict(value = { "stock", "products" }, allEntries = true)
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
      // Update warehouse product location
      WarehouseProduct wp = warehouseProductRepository
          .findById(Objects.requireNonNull(order.getProductId()))
          .orElseThrow(() -> new EntityNotFoundException("Warehouse product not found"));
      wp.setStorageLocation(order.getToLocation());
      warehouseProductRepository.save(wp);

      // Log stock movement
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

  @Transactional
  @CacheEvict(value = { "stock", "products" }, allEntries = true)
  public void stockIn(@NonNull Long productId, int qty, String notes, @NonNull Long userId) {
    Product product = productRepository
        .findById(productId)
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
        "Stock IN: product={} qty={} {}→{}",
        productId,
        qty,
        previousStock,
        product.getStock());
  }

  @Transactional
  @CacheEvict(value = { "stock", "products" }, allEntries = true)
  public void stockOut(@NonNull Long productId, int qty, String notes, @NonNull Long userId) {
    // Pessimistic write lock prevents concurrent oversell
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
        "Stock OUT: product={} qty={} {}→{}",
        productId,
        qty,
        previousStock,
        product.getStock());
  }

  @Transactional
  @CacheEvict(value = { "stock", "products" }, allEntries = true)
  public void stockAdjust(@NonNull Long productId, int qty, String notes, @NonNull Long userId) {
    Product product = productService
        .findByIdWithLock(productId)
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    int previousStock = product.getStock();
    product.setStock(previousStock + qty); // qty can be negative for adjustment
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

  public @NonNull Page<StockMovement> getMovements(@NonNull Pageable pageable) {
    return Objects.requireNonNull(stockMovementRepository.findAllByOrderByCreatedAtDesc(pageable));
  }

  public @NonNull Page<StockMovement> getFilteredMovements(
      @NonNull Long productId, LocalDateTime from, LocalDateTime to, @NonNull Pageable pageable) {
    return Objects.requireNonNull(stockMovementRepository.findByFilters(productId, from, to, pageable));
  }
}
