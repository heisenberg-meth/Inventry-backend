package com.ims.tenant.domain.warehouse;

import com.ims.model.TransferOrder;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.TransferOrderRepository;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import java.util.Objects;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferOrderService {

  private final TransferOrderRepository transferOrderRepository;

  @Transactional
  public @NonNull TransferOrder createTransfer(@NonNull Map<String, Object> request, @NonNull Long userId) {
    Long tenantId = Objects.requireNonNull(TenantContext.getTenantId());
    long productId = Long.parseLong(Objects.requireNonNull(request.get("product_id")).toString());
    String fromLocation = Objects.requireNonNull(request.get("from_location")).toString();
    String toLocation = Objects.requireNonNull(request.get("to_location")).toString();
    int quantity = Integer.parseInt(Objects.requireNonNull(request.get("quantity")).toString());
    String notes = request.getOrDefault("notes", "").toString();

    // Create transfer order
    TransferOrder transfer = TransferOrder.builder()
        .tenantId(tenantId)
        .productId(productId)
        .quantity(quantity)
        .fromLocation(fromLocation)
        .toLocation(toLocation)
        .status("PENDING")
        .notes(notes)
        .createdBy(userId)
        .build();
    transfer = Objects.requireNonNull(transferOrderRepository.save(Objects.requireNonNull(transfer)));

    log.info(
        "Transfer order created (PENDING): id={} product={} quantity={} {} -> {}",
        transfer.getId(),
        productId,
        quantity,
        fromLocation,
        toLocation);
    return transfer;
  }

  @Transactional(readOnly = true)
  public @NonNull Page<TransferOrder> getTransfers(@NonNull Pageable pageable) {
    return Objects.requireNonNull(transferOrderRepository.findAll(pageable));
  }

  @Transactional
  public @NonNull TransferOrder updateStatus(@NonNull Long id, @NonNull String status, @NonNull Long userId) {
    TransferOrder transfer = transferOrderRepository.findById(id)
        .orElseThrow(() -> new jakarta.persistence.EntityNotFoundException("Transfer order not found"));

    String oldStatus = transfer.getStatus();
    if (oldStatus.equals(status)) return transfer;

    // Basic State Machine
    if ("PENDING".equals(oldStatus)) {
        if ("SHIPPED".equals(status)) {
            // Deduct stock from source (assuming source is current stock)
            // In a more complex system, we'd have per-location stock.
            // For now, we'll log it as a stock out from the source location.
            // But we don't actually deduct from the 'global' stock until it leaves the system?
            // Actually, usually SHIPPED means it's in transit.
        } else if (!"CANCELLED".equals(status)) {
            throw new IllegalArgumentException("Cannot transition from PENDING to " + status);
        }
    } else if ("SHIPPED".equals(oldStatus)) {
        if ("RECEIVED".equals(status)) {
            // Logic for receipt
        } else if (!"LOST".equals(status)) {
             throw new IllegalArgumentException("Cannot transition from SHIPPED to " + status);
        }
    }

    transfer.setStatus(status);
    TransferOrder saved = Objects.requireNonNull(transferOrderRepository.save(transfer));
    
    log.info("Transfer order {} updated from {} to {} by user {}", id, oldStatus, status, userId);
    return saved;
  }
}
