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

    /**
     * Cleans up orphaned tenants that are missing required data.
     * A valid tenant MUST have: at least one user, at least one role,
     * and at least one subscription (PRD §10.1).
     * Runs every 5 minutes (PRD §10.2).
     */
    @Scheduled(cron = "0 */5 * * * *")
    @SchedulerLock(name = "cleanup_orphaned_tenants", lockAtMostFor = "30m", lockAtLeastFor = "1m")
    @Transactional
    public void cleanupOrphanedTenants() {
        log.info("Starting orphaned tenant cleanup...");

        String sql = "DELETE FROM tenants t " +
                "WHERE (" +
                "  NOT EXISTS (SELECT 1 FROM users u WHERE u.tenant_id = t.id) " +
                "  OR NOT EXISTS (SELECT 1 FROM subscriptions s WHERE s.tenant_id = t.id) " +
                "  OR NOT EXISTS (SELECT 1 FROM roles r WHERE r.tenant_id = t.id) " +
                ") " +
                "AND t.created_at < NOW() - INTERVAL '10 minutes'";

        int deletedCount = jdbcTemplate.update(sql);

        if (deletedCount > 0) {
            log.warn("Cleaned up {} orphaned/incomplete tenants.", deletedCount);
        }

        log.info("Orphaned tenant cleanup completed.");
    }

    @Transactional
    public void purgeTenant(Long tenantId) {
        log.warn("PERMANENTLY PURGING ALL DATA FOR TENANT: {}", tenantId);
        jdbcTemplate.execute("CALL purge_tenant_data(" + tenantId + ")");
        log.info("Tenant {} data successfully purged from all core tables.", tenantId);
    }
}
