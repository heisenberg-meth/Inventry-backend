package com.ims.tenant.service;

import com.ims.dto.TransferOrderStatusRequest;
import com.ims.model.StockMovement;
import com.ims.model.TransferOrder;
import com.ims.platform.service.TenantService;
import com.ims.product.ProductService;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.domain.warehouse.WarehouseProduct;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.tenant.repository.TransferOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
@Slf4j
public class StockService {

  private final StockMovementRepository stockMovementRepository;
  private final TenantService tenantService;
  private final WarehouseProductRepository warehouseProductRepository;
  private final TransferOrderRepository transferOrderRepository;
  private final com.ims.product.ProductRepository productRepository;
  private final ProductService productService;
  private final StockTransactionService stockTransactionService;

  private void checkWarehouseType() {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("No tenant context found");
    }
    if (!tenantService.isWarehouse(tenantId)) {
      throw new IllegalArgumentException("Only available for WAREHOUSE tenants");
    }
  }

  @Cacheable(
      value = "stock",
      key = "'location:' + #location",
      cacheResolver = "tenantAwareCacheResolver")
  public @NonNull Page<WarehouseProduct> getProductsByLocation(
      @NonNull String location, @NonNull Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant ID is missing in StockService.getProductsByLocation");
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    checkWarehouseType();
    return Objects.requireNonNull(warehouseProductRepository.findByLocation(location, pageable));
  }

  public @NonNull Page<TransferOrder> getTransferOrders(@NonNull Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant ID is missing in StockService.getTransferOrders");
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    checkWarehouseType();
    return Objects.requireNonNull(transferOrderRepository.findAll(pageable));
  }

  @Cacheable(value = "stock", key = "'order:' + #id", cacheResolver = "tenantAwareCacheResolver")
  public @NonNull TransferOrder getTransferOrderById(@NonNull Long id) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant ID is missing in StockService.getTransferOrderById");
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    checkWarehouseType();
    return Objects.requireNonNull(
        transferOrderRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Transfer Order not found")));
  }

  public @NonNull TransferOrder updateTransferStatus(
      @NonNull Long id, @NonNull TransferOrderStatusRequest request, @NonNull Long userId) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant ID is missing in StockService.updateTransferStatus");
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    return stockTransactionService.updateTransferStatus(id, request, userId);
  }

  public void stockIn(@NonNull Long productId, int qty, String notes, @NonNull Long userId) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant ID is missing in StockService.stockIn");
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    stockTransactionService.stockInInternal(productId, qty, notes, userId);
  }

  public void stockOut(@NonNull Long productId, int qty, String notes, @NonNull Long userId) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant ID is missing in StockService.stockOut");
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    stockTransactionService.stockOutInternal(productId, qty, notes, userId);
  }

  public void stockAdjust(@NonNull Long productId, int qty, String notes, @NonNull Long userId) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant ID is missing in StockService.stockAdjust");
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    stockTransactionService.stockAdjustInternal(productId, qty, notes, userId);
  }

  public @NonNull Page<StockMovement> getMovements(@NonNull Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant ID is missing in StockService.getMovements");
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    return Objects.requireNonNull(stockMovementRepository.findAllByOrderByCreatedAtDesc(pageable));
  }

  public @NonNull Page<StockMovement> getFilteredMovements(
      @NonNull Long productId, LocalDateTime from, LocalDateTime to, @NonNull Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      log.warn("Tenant ID is missing in StockService.getFilteredMovements");
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    return Objects.requireNonNull(
        stockMovementRepository.findByFilters(productId, from, to, pageable));
  }
}
