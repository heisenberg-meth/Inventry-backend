package com.ims.tenant.service;

import com.ims.product.Product;
import com.ims.product.ProductService;
import com.ims.product.ProductRepository;
import com.ims.model.StockMovement;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.exception.InsufficientStockException;
import com.ims.tenant.repository.StockMovementRepository;
import jakarta.persistence.EntityNotFoundException;
import java.time.LocalDateTime;
import lombok.RequiredArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
@Slf4j
public class StockTransactionService {

  private final ProductService productService;
  private final ProductRepository productRepository;
  private final StockMovementRepository stockMovementRepository;
  private final com.ims.shared.notification.AlertService alertService;

  @Transactional
  public void stockInInternal(Long productId, int qty, String notes, Long userId) {
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
            .tenantId(TenantContext.getTenantId())
            .movementType("IN")
            .quantity(qty)
            .previousStock(previousStock)
            .newStock(product.getStock())
            .notes(notes)
            .createdBy(userId)
            .build());

    log.info("Stock IN: product={} qty={} {}→{}", productId, qty, previousStock, product.getStock());
  }

  @Transactional
  public void stockOutInternal(Long productId, int qty, String notes, Long userId) {
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

    // Trigger low stock check
    alertService.checkLowStock(product);

    stockMovementRepository.save(
        StockMovement.builder()
            .productId(productId)
            .tenantId(TenantContext.getTenantId())
            .movementType("OUT")
            .quantity(qty)
            .previousStock(previousStock)
            .newStock(product.getStock())
            .notes(notes)
            .createdBy(userId)
            .build());

    log.info("Stock OUT: product={} qty={} {}→{}", productId, qty, previousStock, product.getStock());
  }

  @Transactional
  public void stockAdjustInternal(Long productId, int qty, String notes, Long userId) {
    Product product = productService
        .findByIdWithLock(productId)
        .orElseThrow(() -> new EntityNotFoundException("Product not found"));

    int previousStock = product.getStock();
    int newStock = previousStock + qty;

    if (newStock < 0) {
      throw new InsufficientStockException(
          "Stock adjustment would result in negative stock: " + newStock,
          previousStock,
          Math.abs(qty));
    }

    product.setStock(newStock);
    product.setUpdatedAt(LocalDateTime.now());
    productRepository.save(product);

    if (qty < 0) {
      alertService.checkLowStock(product);
    }

    stockMovementRepository.save(
        StockMovement.builder()
            .productId(productId)
            .tenantId(TenantContext.getTenantId())
            .movementType("ADJUSTMENT")
            .quantity(qty)
            .previousStock(previousStock)
            .newStock(product.getStock())
            .notes(notes)
            .createdBy(userId)
            .build());

    log.info("Stock ADJUST: product={} qty={} {}→{}", productId, qty, previousStock, product.getStock());
  }
}
