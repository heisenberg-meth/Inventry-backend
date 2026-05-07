package com.ims.tenant.service;

import com.ims.product.Product;
import com.ims.product.ProductRepository;
import com.ims.tenant.repository.StockMovementRepository;
import lombok.RequiredArgsConstructor;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;

@Service
@RequiredArgsConstructor
@NoArgsConstructor(force = true)
@Slf4j
public class StockReconciliationService {

    private final ProductRepository productRepository;
    private final StockMovementRepository stockMovementRepository;
    private final com.ims.platform.repository.TenantRepository tenantRepository;

    @Scheduled(cron = "0 0 1 * * ?") // Every day at 1 AM
    @Transactional
    public void reconcileAllStock() {
        log.info("Starting daily stock reconciliation...");
        
        var tenants = tenantRepository.findAll();
        for (var tenant : tenants) {
            com.ims.shared.auth.TenantContext.setTenantId(tenant.getId());
            try {
                log.debug("Reconciling stock for tenant: {}", tenant.getName());
                List<Product> products = productRepository.findAll();
                for (Product product : products) {
                    reconcileProduct(product);
                }
            } finally {
                com.ims.shared.auth.TenantContext.clear();
            }
        }
        
        log.info("Stock reconciliation completed.");
    }

    private void reconcileProduct(Product product) {
        Integer calculatedStock = stockMovementRepository.calculateStock(product.getId(), product.getTenantId());
        if (calculatedStock == null) {
            calculatedStock = 0;
        }

        if (product.getStock() != calculatedStock) {
            log.error("Stock mismatch for product {} (ID: {}). DB Stock: {}, Calculated: {}",
                    product.getName(), product.getId(), product.getStock(), calculatedStock);
            
            // In a real app, you might auto-adjust or flag for review
            // product.setStock(calculatedStock);
            // productRepository.save(product);
        }
    }
}
