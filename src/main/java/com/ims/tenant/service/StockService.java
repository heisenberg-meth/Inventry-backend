package com.ims.tenant.service;

import com.ims.dto.TransferOrderStatusRequest;
import com.ims.model.StockMovement;
import com.ims.model.TransferOrder;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.domain.warehouse.WarehouseProduct;
import com.ims.platform.service.TenantService;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.tenant.repository.TransferOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
@Slf4j

public class StockService {

  private final StockMovementRepository stockMovementRepository;
  private final TenantService tenantService;
  private final WarehouseProductRepository warehouseProductRepository;
  private final TransferOrderRepository transferOrderRepository;
  private final StockTransactionService stockTransactionService;

  private void checkWarehouseType() {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("No tenant context found");
    }
    if (!tenantService.isWarehouse(tenantId)) {
      throw new IllegalArgumentException("Only available for WAREHOUSE tenants");
    }
  }

  public Page<WarehouseProduct> getProductsByLocation(String location, Pageable pageable) {
    checkWarehouseType();
    return Objects.requireNonNull(warehouseProductRepository.findByLocation(location, pageable));
  }

  public Page<TransferOrder> getTransferOrders(Pageable pageable) {
    checkWarehouseType();
    return Objects.requireNonNull(transferOrderRepository.findAll(pageable));
  }

  public TransferOrder getTransferOrderById(Long id) {
    checkWarehouseType();
    return Objects.requireNonNull(transferOrderRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Transfer Order not found")));
  }

  @Transactional
  @CacheEvict(value = { "stock", "products" }, allEntries = true)
  public TransferOrder updateTransferStatus(Long id, TransferOrderStatusRequest request,
      Long userId) {
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
              .tenantId(TenantContext.getTenantId())
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
  public void stockIn(Long productId, int qty, String notes, Long userId) {
    stockTransactionService.stockInInternal(productId, qty, notes, userId);
  }

  @Transactional
  @CacheEvict(value = { "stock", "products" }, allEntries = true)
  public void stockOut(Long productId, int qty, String notes, Long userId) {
    stockTransactionService.stockOutInternal(productId, qty, notes, userId);
  }

  @Transactional
  @CacheEvict(value = { "stock", "products" }, allEntries = true)
  public void stockAdjust(Long productId, int qty, String notes, Long userId) {
    stockTransactionService.stockAdjustInternal(productId, qty, notes, userId);
  }

  public Page<StockMovement> getMovements(Pageable pageable) {
    return Objects.requireNonNull(stockMovementRepository.findAllByOrderByCreatedAtDesc(pageable));
  }

  public Page<StockMovement> getFilteredMovements(
      Long productId, LocalDateTime from, LocalDateTime to, Pageable pageable) {
    return Objects.requireNonNull(stockMovementRepository.findByFilters(productId, from, to, pageable));
  }
}