package com.ims.tenant.domain.warehouse;

import com.ims.model.StockMovement;
import com.ims.model.TransferOrder;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.tenant.repository.TransferOrderRepository;
import com.ims.tenant.service.WarehouseProductRepository;
import jakarta.persistence.EntityNotFoundException;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.lang.NonNull;
import java.util.Objects;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferOrderService {

  private final TransferOrderRepository transferOrderRepository;
  private final WarehouseProductRepository warehouseProductRepository;
  private final StockMovementRepository stockMovementRepository;

  @Transactional
  public @NonNull TransferOrder createTransfer(@NonNull Map<String, Object> request, @NonNull Long userId) {
    Long tenantId = Objects.requireNonNull(TenantContext.get());
    long productId = Long.parseLong(Objects.requireNonNull(request.get("product_id")).toString());
    String fromLocation = Objects.requireNonNull(request.get("from_location")).toString();
    String toLocation = Objects.requireNonNull(request.get("to_location")).toString();
    int quantity = Integer.parseInt(Objects.requireNonNull(request.get("quantity")).toString());
    String notes = request.getOrDefault("notes", "").toString();

    // Create transfer order
    TransferOrder transfer =
        TransferOrder.builder()
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
}
