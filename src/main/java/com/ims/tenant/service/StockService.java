package com.ims.tenant.service;

import com.ims.dto.TransferOrderStatusRequest;
import com.ims.model.StockMovement;
import com.ims.model.TransferOrder;
import com.ims.platform.service.TenantService;
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
import org.springframework.stereotype.Service;

@Service
@RequiredArgsConstructor
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
      throw new com.ims.shared.exception.TenantContextException("No tenant context found");
    }
    if (!tenantService.isWarehouse(tenantId)) {
      throw new IllegalArgumentException("Only available for WAREHOUSE tenants");
    }
  }

  @Cacheable(value = "stock", key = "'location:' + #location", cacheResolver = "tenantAwareCacheResolver")
  public  Page<WarehouseProduct> getProductsByLocation(
       String location,  Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    checkWarehouseType();
    return Objects.requireNonNull(warehouseProductRepository.findByLocation(location, pageable));
  }

  public Page<TransferOrder> getTransferOrders(Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    checkWarehouseType();
    return Objects.requireNonNull(transferOrderRepository.findAll(pageable));
  }

  @Cacheable(value = "stock", key = "'order:' + #id", cacheResolver = "tenantAwareCacheResolver")
  public TransferOrder getTransferOrderById(Long id) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    checkWarehouseType();
    return Objects.requireNonNull(
        transferOrderRepository
            .findById(id)
            .orElseThrow(() -> new EntityNotFoundException("Transfer Order not found")));
  }

  public TransferOrder updateTransferStatus(
      Long id, TransferOrderStatusRequest request, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    return stockTransactionService.updateTransferStatus(id, request, userId);
  }

  public void stockIn(Long productId, int qty, String notes, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    stockTransactionService.stockInInternal(productId, qty, notes, userId);
  }

  public void stockOut(Long productId, int qty, String notes, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    stockTransactionService.stockOutInternal(productId, qty, notes, userId);
  }

  public void stockAdjust(Long productId, int qty, String notes, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    stockTransactionService.stockAdjustInternal(productId, qty, notes, userId);
  }

  public Page<StockMovement> getMovements(Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    return Objects.requireNonNull(stockMovementRepository.findAllByOrderByCreatedAtDesc(pageable));
  }

  public Page<StockMovement> getFilteredMovements(
      Long productId, LocalDateTime from, LocalDateTime to, Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new com.ims.shared.exception.TenantContextException("Tenant context is missing");
    }

    return Objects.requireNonNull(
        stockMovementRepository.findByFilters(productId, from, to, pageable));
  }
}
