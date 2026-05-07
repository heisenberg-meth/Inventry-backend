package com.ims.tenant.service;

import com.ims.dto.InventoryResponse;
import com.ims.dto.StockReservationRequest;
import com.ims.dto.StockReservationResponse;
import com.ims.model.Inventory;
import com.ims.model.StockMovement;
import com.ims.product.Product;
import com.ims.product.ProductRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.InsufficientStockException;
import com.ims.shared.notification.AlertService;
import com.ims.tenant.repository.InventoryRepository;
import com.ims.tenant.repository.StockMovementRepository;
import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import jakarta.persistence.EntityNotFoundException;
import jakarta.persistence.OptimisticLockException;
import jakarta.persistence.PessimisticLockException;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.orm.ObjectOptimisticLockingFailureException;
import org.springframework.retry.annotation.Backoff;
import org.springframework.retry.annotation.Retryable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
public class InventoryService {

  private final InventoryRepository inventoryRepository;
  private final ProductRepository productRepository;
  private final StockMovementRepository stockMovementRepository;
  private final AlertService alertService;

  private final Counter stockConflictCounter;
  private final Counter lowStockAlertCounter;

  public InventoryService(
      InventoryRepository inventoryRepository,
      ProductRepository productRepository,
      StockMovementRepository stockMovementRepository,
      AlertService alertService,
      MeterRegistry meterRegistry) {
    this.inventoryRepository = inventoryRepository;
    this.productRepository = productRepository;
    this.stockMovementRepository = stockMovementRepository;
    this.alertService = alertService;
    this.stockConflictCounter = Counter.builder("inventory.stock_conflicts").register(meterRegistry);
    this.lowStockAlertCounter = Counter.builder("inventory.low_stock_alerts").register(meterRegistry);
  }

  @Transactional(readOnly = true)
  public InventoryResponse getInventoryByProductId(Long productId) {
    Long tenantId = TenantContext.getTenantId();
    Inventory inventory = inventoryRepository
        .findByProductIdAndTenantId(productId, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Inventory not found for product: " + productId));
    return toResponse(inventory);
  }

  @Transactional(readOnly = true)
  public Page<InventoryResponse> getAllInventories(Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    return inventoryRepository.findAllByTenantId(tenantId, pageable).map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public Page<InventoryResponse> getLowStockInventories(Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    return inventoryRepository.findLowStockByTenantId(tenantId, pageable).map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public Page<InventoryResponse> getReorderLevelInventories(Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    return inventoryRepository.findBelowReorderLevelByTenantId(tenantId, pageable).map(this::toResponse);
  }

  @Transactional(readOnly = true)
  public Integer getAvailableStock(Long productId) {
    Long tenantId = TenantContext.getTenantId();
    return inventoryRepository
        .findByProductIdAndTenantId(productId, tenantId)
        .map(Inventory::getAvailableQuantity)
        .orElse(0);
  }

  @Transactional
  @Retryable(retryFor = { OptimisticLockException.class,
      ObjectOptimisticLockingFailureException.class, PessimisticLockException.class }, maxAttempts = 3,
      backoff = @Backoff(delay = 200, maxDelay = 1000, multiplier = 2))
  public InventoryResponse increaseStock(Long productId, int quantity, String notes, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    log.info("Increasing stock: product={} qty={} tenant={}", productId, quantity, tenantId);

    Inventory inventory = inventoryRepository
        .findByProductIdAndTenantIdWithLock(productId, tenantId)
        .orElseGet(() -> createInventory(productId, tenantId));

    int previousQuantity = inventory.getQuantity();
    inventory.setQuantity(previousQuantity + quantity);

    inventory = inventoryRepository.save(inventory);
    syncProductStock(productId, inventory.getQuantity());

    recordStockMovement(productId, "IN", quantity, previousQuantity, inventory.getQuantity(), notes, userId);

    log.info("Stock increased: product={} {}→{} tenant={}", productId, previousQuantity, inventory.getQuantity(),
        tenantId);
    return toResponse(inventory);
  }

  @Transactional
  @Retryable(retryFor = { OptimisticLockException.class,
      ObjectOptimisticLockingFailureException.class, PessimisticLockException.class }, maxAttempts = 3,
      backoff = @Backoff(delay = 200, maxDelay = 1000, multiplier = 2))
  public InventoryResponse decreaseStock(Long productId, int quantity, String notes, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    log.info("Decreasing stock: product={} qty={} tenant={}", productId, quantity, tenantId);

    Inventory inventory = inventoryRepository
        .findByProductIdAndTenantIdWithLock(productId, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Inventory not found for product: " + productId));

    int previousQuantity = inventory.getQuantity();
    int availableQuantity = inventory.getAvailableQuantity();

    if (availableQuantity < quantity) {
      stockConflictCounter.increment();
      throw new InsufficientStockException(
          "Insufficient stock. Requested: " + quantity + ", Available: " + availableQuantity,
          availableQuantity,
          quantity);
    }

    inventory.setQuantity(previousQuantity - quantity);
    inventory = inventoryRepository.save(inventory);
    syncProductStock(productId, inventory.getQuantity());

    recordStockMovement(productId, "OUT", quantity, previousQuantity, inventory.getQuantity(), notes, userId);

    checkLowStockAndAlert(inventory);

    log.info("Stock decreased: product={} {}→{} tenant={}", productId, previousQuantity, inventory.getQuantity(),
        tenantId);
    return toResponse(inventory);
  }

  @Transactional
  @Retryable(retryFor = { OptimisticLockException.class,
      ObjectOptimisticLockingFailureException.class, PessimisticLockException.class }, maxAttempts = 3,
      backoff = @Backoff(delay = 200, maxDelay = 1000, multiplier = 2))
  public InventoryResponse adjustStock(Long productId, int quantity, String notes, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    log.info("Adjusting stock: product={} qty={} tenant={}", productId, quantity, tenantId);

    Inventory inventory = inventoryRepository
        .findByProductIdAndTenantIdWithLock(productId, tenantId)
        .orElseGet(() -> createInventory(productId, tenantId));

    int previousQuantity = inventory.getQuantity();
    int newQuantity = previousQuantity + quantity;

    if (newQuantity < 0) {
      stockConflictCounter.increment();
      throw new InsufficientStockException(
          "Stock adjustment would result in negative stock: " + newQuantity,
          previousQuantity,
          Math.abs(quantity));
    }

    inventory.setQuantity(newQuantity);
    inventory = inventoryRepository.save(inventory);
    syncProductStock(productId, inventory.getQuantity());

    String movementType = quantity > 0 ? "ADJUSTMENT_IN" : "ADJUSTMENT_OUT";
    recordStockMovement(productId, movementType, Math.abs(quantity), previousQuantity, inventory.getQuantity(), notes,
        userId);

    if (quantity < 0) {
      checkLowStockAndAlert(inventory);
    }

    log.info("Stock adjusted: product={} {}→{} tenant={}", productId, previousQuantity, inventory.getQuantity(),
        tenantId);
    return toResponse(inventory);
  }

  @Transactional
  @Retryable(retryFor = { OptimisticLockException.class,
      ObjectOptimisticLockingFailureException.class, PessimisticLockException.class }, maxAttempts = 3,
      backoff = @Backoff(delay = 200, maxDelay = 1000, multiplier = 2))
  public StockReservationResponse reserveStock(StockReservationRequest request) {
    Long tenantId = TenantContext.getTenantId();
    Long productId = request.getProductId();
    int quantity = request.getQuantity();

    log.info("Reserving stock: product={} qty={} tenant={}", productId, quantity, tenantId);

    Inventory inventory = inventoryRepository
        .findByProductIdAndTenantIdWithLock(productId, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Inventory not found for product: " + productId));

    int availableQuantity = inventory.getAvailableQuantity();
    if (availableQuantity < quantity) {
      stockConflictCounter.increment();
      throw new InsufficientStockException(
          "Cannot reserve. Requested: " + quantity + ", Available: " + availableQuantity,
          availableQuantity,
          quantity);
    }

    int previousReserved = inventory.getReservedQuantity();
    inventory.setReservedQuantity(previousReserved + quantity);
    inventory = inventoryRepository.save(inventory);

    recordStockMovement(productId, "RESERVE", quantity, previousReserved, inventory.getReservedQuantity(),
        "Stock reservation", request.getUserId());

    log.info("Stock reserved: product={} qty={} reserved={} tenant={}", productId, quantity,
        inventory.getReservedQuantity(), tenantId);

    return StockReservationResponse.builder()
        .productId(productId)
        .reservedQuantity(quantity)
        .availableAfterReservation(inventory.getAvailableQuantity())
        .reservationId(java.util.UUID.randomUUID().toString())
        .build();
  }

  @Transactional
  @Retryable(retryFor = { OptimisticLockException.class,
      ObjectOptimisticLockingFailureException.class, PessimisticLockException.class }, maxAttempts = 3,
      backoff = @Backoff(delay = 200, maxDelay = 1000, multiplier = 2))
  public InventoryResponse releaseReservation(Long productId, int quantity, String notes, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    log.info("Releasing reservation: product={} qty={} tenant={}", productId, quantity, tenantId);

    Inventory inventory = inventoryRepository
        .findByProductIdAndTenantIdWithLock(productId, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Inventory not found for product: " + productId));

    int previousReserved = inventory.getReservedQuantity();
    int releaseQty = Math.min(quantity, previousReserved);

    inventory.setReservedQuantity(previousReserved - releaseQty);
    inventory = inventoryRepository.save(inventory);

    recordStockMovement(productId, "RELEASE", releaseQty, previousReserved, inventory.getReservedQuantity(),
        notes != null ? notes : "Reservation release", userId);

    log.info("Reservation released: product={} released={} remaining={} tenant={}",
        productId, releaseQty, inventory.getReservedQuantity(), tenantId);

    return toResponse(inventory);
  }

  @Transactional
  @Retryable(retryFor = { OptimisticLockException.class,
      ObjectOptimisticLockingFailureException.class, PessimisticLockException.class }, maxAttempts = 3,
      backoff = @Backoff(delay = 200, maxDelay = 1000, multiplier = 2))
  public InventoryResponse fulfillReservation(Long productId, int quantity, String notes, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    log.info("Fulfilling reservation: product={} qty={} tenant={}", productId, quantity, tenantId);

    Inventory inventory = inventoryRepository
        .findByProductIdAndTenantIdWithLock(productId, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Inventory not found for product: " + productId));

    int previousReserved = inventory.getReservedQuantity();
    int fulfillQty = Math.min(quantity, previousReserved);

    inventory.setQuantity(inventory.getQuantity() - fulfillQty);
    inventory.setReservedQuantity(previousReserved - fulfillQty);
    inventory = inventoryRepository.save(inventory);
    syncProductStock(productId, inventory.getQuantity());

    recordStockMovement(productId, "FULFILL", fulfillQty, previousReserved, inventory.getReservedQuantity(),
        notes != null ? notes : "Reservation fulfilled", userId);

    checkLowStockAndAlert(inventory);

    log.info("Reservation fulfilled: product={} fulfilled={} qty={} tenant={}",
        productId, fulfillQty, inventory.getQuantity(), tenantId);

    return toResponse(inventory);
  }

  @Transactional
  public InventoryResponse updateThresholds(Long productId, Integer lowStockThreshold, Integer reorderLevel) {
    Long tenantId = TenantContext.getTenantId();
    Inventory inventory = inventoryRepository
        .findByProductIdAndTenantId(productId, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Inventory not found for product: " + productId));

    if (lowStockThreshold != null) {
      inventory.setLowStockThreshold(lowStockThreshold);
    }
    if (reorderLevel != null) {
      inventory.setReorderLevel(reorderLevel);
    }

    inventory = inventoryRepository.save(inventory);
    log.info("Thresholds updated: product={} lowStock={} reorder={} tenant={}",
        productId, lowStockThreshold, reorderLevel, tenantId);

    return toResponse(inventory);
  }

  private Inventory createInventory(Long productId, Long tenantId) {
    Product product = productRepository.findById(productId)
        .orElseThrow(() -> new EntityNotFoundException("Product not found: " + productId));

    Inventory inventory = Inventory.builder()
        .tenantId(tenantId)
        .productId(productId)
        .quantity(0)
        .reservedQuantity(0)
        .lowStockThreshold(10)
        .reorderLevel(product.getReorderLevel() != null ? product.getReorderLevel() : 10)
        .build();

    return inventoryRepository.save(inventory);
  }

  private void recordStockMovement(Long productId, String movementType, int quantity,
      int previousStock, int newStock, String notes, Long userId) {
    Long tenantId = TenantContext.getTenantId();
    StockMovement movement = StockMovement.builder()
        .tenantId(tenantId)
        .productId(productId)
        .movementType(movementType)
        .quantity(quantity)
        .previousStock(previousStock)
        .newStock(newStock)
        .notes(notes)
        .createdBy(userId)
        .build();
    stockMovementRepository.save(movement);
  }

  private void checkLowStockAndAlert(Inventory inventory) {
    if (inventory.isLowStock()) {
      lowStockAlertCounter.increment();
      alertService.checkLowStock(inventory);
    }
  }

  private void syncProductStock(Long productId, int quantity) {
    productRepository.findById(productId).ifPresent(p -> {
      p.setStock(quantity);
      productRepository.save(p);
    });
  }

  private InventoryResponse toResponse(Inventory inventory) {
    return InventoryResponse.builder()
        .id(inventory.getId())
        .productId(inventory.getProductId())
        .quantity(inventory.getQuantity())
        .reservedQuantity(inventory.getReservedQuantity())
        .availableQuantity(inventory.getAvailableQuantity())
        .lowStockThreshold(inventory.getLowStockThreshold())
        .reorderLevel(inventory.getReorderLevel())
        .isLowStock(inventory.isLowStock())
        .isBelowReorderLevel(inventory.isBelowReorderLevel())
        .version(inventory.getVersion())
        .build();
  }
}
