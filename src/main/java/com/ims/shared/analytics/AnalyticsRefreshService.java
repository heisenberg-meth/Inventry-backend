package com.ims.shared.analytics;

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
public class AnalyticsRefreshService {

    private final JdbcTemplate jdbcTemplate;

    /**
     * Refreshes the tenant summary stats materialized view every 15 minutes.
     * Uses ShedLock to ensure only one instance refreshes the view in a cluster.
     */
    @Scheduled(cron = "0 0/15 * * * *")
    @SchedulerLock(name = "refresh_tenant_summary_stats", lockAtMostFor = "10m", lockAtLeastFor = "1m")
    @Transactional
    public void refreshStats() {
        log.info("Refreshing tenant_summary_stats materialized view...");
        try {
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW CONCURRENTLY tenant_summary_stats");
            log.info("Successfully refreshed tenant_summary_stats.");
        } catch (Exception e) {
            log.error("Failed to refresh materialized view. Falling back to non-concurrent refresh.", e);
            // Fallback if CONCURRENTLY fails (e.g., if no unique index exists yet, though
            // we created one)
            jdbcTemplate.execute("REFRESH MATERIALIZED VIEW tenant_summary_stats");
        }
    }
}
