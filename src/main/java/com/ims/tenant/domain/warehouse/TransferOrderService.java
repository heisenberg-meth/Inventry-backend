package com.ims.tenant.domain.warehouse;

import com.ims.model.StockMovement;
import com.ims.model.TransferOrder;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.StockMovementRepository;
import com.ims.tenant.repository.TransferOrderRepository;
import com.ims.tenant.service.WarehouseProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.Map;

@Service
@RequiredArgsConstructor
@Slf4j
public class TransferOrderService {

    private final TransferOrderRepository transferOrderRepository;
    private final WarehouseProductRepository warehouseProductRepository;
    private final StockMovementRepository stockMovementRepository;

    @Transactional
    public TransferOrder createTransfer(Map<String, Object> request, Long userId) {
        Long tenantId = TenantContext.get();
        Long productId = Long.valueOf(request.get("product_id").toString());
        String fromLocation = request.get("from_location").toString();
        String toLocation = request.get("to_location").toString();
        int quantity = Integer.parseInt(request.get("quantity").toString());
        String notes = request.getOrDefault("notes", "").toString();

        // Create transfer order
        TransferOrder transfer = TransferOrder.builder()
                .tenantId(tenantId)
                .fromLocation(fromLocation)
                .toLocation(toLocation)
                .status("COMPLETED")
                .notes(notes)
                .createdBy(userId)
                .build();
        transfer = transferOrderRepository.save(transfer);

        // Update warehouse product location
        WarehouseProduct wp = warehouseProductRepository.findById(productId)
                .orElseThrow(() -> new EntityNotFoundException("Warehouse product not found"));
        wp.setStorageLocation(toLocation);
        warehouseProductRepository.save(wp);

        // Log stock movement
        stockMovementRepository.save(StockMovement.builder()
                .tenantId(tenantId)
                .productId(productId)
                .movementType("TRANSFER")
                .quantity(quantity)
                .notes("Transfer from " + fromLocation + " to " + toLocation)
                .createdBy(userId)
                .referenceId(transfer.getId())
                .referenceType("TRANSFER_ORDER")
                .build());

        log.info("Transfer order created: id={} product={} {} -> {}", transfer.getId(), productId, fromLocation, toLocation);
        return transfer;
    }
}
