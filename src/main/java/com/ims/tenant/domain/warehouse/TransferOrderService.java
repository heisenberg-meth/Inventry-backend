package com.ims.tenant.domain.warehouse;

import com.ims.model.TransferOrder;
import com.ims.model.TransferOrderStatus;
import com.ims.tenant.repository.TransferOrderRepository;
import jakarta.persistence.EntityNotFoundException;
import com.ims.tenant.dto.TransferOrderRequest;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
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
  public TransferOrder createTransfer(
      TransferOrderRequest request, Long userId) {
    long productId = Objects.requireNonNull(request.getProductId());
    String fromLocation = Objects.requireNonNull(request.getFromLocation());
    String toLocation = Objects.requireNonNull(request.getToLocation());
    int quantity = Objects.requireNonNull(request.getQuantity());
    String notes = Objects.requireNonNull(request.getNotes() != null ? request.getNotes() : "");

    // Create transfer order
    TransferOrder transfer = TransferOrder.builder()
        .productId(productId)
        .quantity(quantity)
        .fromLocation(fromLocation)
        .toLocation(toLocation)
        .status(TransferOrderStatus.PENDING)
        .notes(notes)
        .createdBy(userId)
        .build();
    TransferOrder saved = transferOrderRepository.save(Objects.requireNonNull(transfer));
    transfer = Objects.requireNonNull(saved);

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
  public Page<TransferOrder> getTransfers(Pageable pageable) {
    return Objects.requireNonNull(transferOrderRepository.findAll(pageable));
  }

  @Transactional
  public TransferOrder updateStatus(
      Long id, TransferOrderStatus status, Long userId) {
    TransferOrder transfer = transferOrderRepository
        .findById(id)
        .orElseThrow(
            () -> new EntityNotFoundException("Transfer order not found"));

    TransferOrderStatus oldStatus = transfer.getStatus();
    if (oldStatus == status) {
      return transfer;
    }

    // Basic State Machine
    if (TransferOrderStatus.PENDING.equals(oldStatus)) {
      if (TransferOrderStatus.SHIPPED.equals(status)) {
        // ...
      } else if (TransferOrderStatus.CANCELLED != status) {
        throw new IllegalArgumentException("Cannot transition from PENDING to " + status);
      }
    } else if (TransferOrderStatus.SHIPPED.equals(oldStatus)) {
      if (TransferOrderStatus.RECEIVED.equals(status)) {
        // ...
      } else if (TransferOrderStatus.LOST != status) {
        throw new IllegalArgumentException("Cannot transition from SHIPPED to " + status);
      }
    }

    transfer.setStatus(status);
    TransferOrder saved = Objects.requireNonNull(transferOrderRepository.save(transfer));

    log.info("Transfer order {} updated from {} to {} by user {}", id, oldStatus, status, userId);
    return saved;
  }
}
