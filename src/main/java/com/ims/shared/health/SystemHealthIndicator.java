package com.ims.shared.health;

import org.springframework.boot.actuate.health.Health;
import org.springframework.boot.actuate.health.HealthIndicator;
import org.springframework.stereotype.Component;
import java.io.File;

/**
 * Custom health indicator to monitor critical system resources.
 * Specifically monitors disk space for PDF generation and logging capacity.
 */
@Component
public class SystemHealthIndicator implements HealthIndicator {

    private static final long MIN_DISK_SPACE_BYTES = 500 * 1024 * 1024; // 500MB

    @Override
    public Health health() {
        File root = new File("/");
        long freeSpace = root.getFreeSpace();
        long totalSpace = root.getTotalSpace();

        Health.Builder builder = (freeSpace > MIN_DISK_SPACE_BYTES) ? Health.up() : Health.down();

        return builder
                .withDetail("disk_free", freeSpace)
                .withDetail("disk_total", totalSpace)
                .withDetail("disk_threshold", MIN_DISK_SPACE_BYTES)
                .build();
    }
}
