package com.ims.shared.maintenance;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import net.javacrumbs.shedlock.spring.annotation.SchedulerLock;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class MaintenanceService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Moves logs older than 1 year to archive tables.
     * Runs once a month at midnight on the 1st.
     */
    @Scheduled(cron = "0 0 0 1 * *")
    @SchedulerLock(name = "archive_old_logs", lockAtMostFor = "1h", lockAtLeastFor = "10m")
    @Transactional
    public void archiveOldLogs() {
        log.info("Starting log archival process...");

        // We use a safe interval for production archiving
        String cutoffDate = "NOW() - INTERVAL '1 year'";

        try {
            // Archive Audit Logs
            int archivedAudit = jdbcTemplate.update(
                    "INSERT INTO audit_logs_archive (id, tenant_id, user_id, action, resource_type, resource_id, details, created_at) "
                            +
                            "SELECT id, tenant_id, user_id, action, resource_type, resource_id, details, created_at FROM audit_logs "
                            +
                            "WHERE created_at < " + cutoffDate);

            if (archivedAudit > 0) {
                jdbcTemplate.update("DELETE FROM audit_logs WHERE created_at < " + cutoffDate);
                log.info("Archived {} audit logs.", archivedAudit);
            }

            // Archive Stock Movements
            int archivedStock = jdbcTemplate.update(
                    "INSERT INTO stock_movements_archive (id, tenant_id, product_id, movement_type, quantity, previous_stock, new_stock, reference_id, reference_type, notes, created_by, created_at) "
                            +
                            "SELECT id, tenant_id, product_id, movement_type, quantity, previous_stock, new_stock, reference_id, reference_type, notes, created_by, created_at FROM stock_movements "
                            +
                            "WHERE created_at < " + cutoffDate);

            if (archivedStock > 0) {
                jdbcTemplate.update("DELETE FROM stock_movements WHERE created_at < " + cutoffDate);
                log.info("Archived {} stock movements.", archivedStock);
            }
        } catch (Exception e) {
            log.error("Maintenance task failed during archival", e);
        }

        log.info("Log archival completed.");
    }

    @Transactional
    public void purgeTenant(Long tenantId) {
        log.warn("PERMANENTLY PURGING ALL DATA FOR TENANT: {}", tenantId);
        jdbcTemplate.execute("CALL purge_tenant_data(" + tenantId + ")");
        log.info("Tenant {} data successfully purged from all core tables.", tenantId);
    }
}
