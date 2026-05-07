package com.ims.shared.cache;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.concurrent.ConcurrentHashMap;

@Service
@RequiredArgsConstructor
@Slf4j
public class CacheMetricsService {

    private final MeterRegistry meterRegistry;
    private final ConcurrentHashMap<String, Counter> hitCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Counter> missCounters = new ConcurrentHashMap<>();
    private final ConcurrentHashMap<String, Timer> latencyTimers = new ConcurrentHashMap<>();

    public void recordHit(String cacheName) {
        hitCounters.computeIfAbsent(cacheName, this::createHitCounter).increment();
    }

    public void recordMiss(String cacheName) {
        missCounters.computeIfAbsent(cacheName, this::createMissCounter).increment();
    }

    public void recordLatency(String cacheName, long millis) {
        latencyTimers.computeIfAbsent(cacheName, this::createLatencyTimer).record(millis, java.util.concurrent.TimeUnit.MILLISECONDS);
    }

    public double getHitRatio(String cacheName) {
        Counter hitCounter = hitCounters.get(cacheName);
        Counter missCounter = missCounters.get(cacheName);
        if (hitCounter == null && missCounter == null) {
            return 0.0;
        }
        double hits = hitCounter != null ? hitCounter.count() : 0;
        double misses = missCounter != null ? missCounter.count() : 0;
        double total = hits + misses;
        return total > 0 ? hits / total : 0.0;
    }

    private Counter createHitCounter(String cacheName) {
        return Counter.builder("cache.hits")
                .tag("cache", cacheName)
                .description("Number of cache hits")
                .register(meterRegistry);
    }

    private Counter createMissCounter(String cacheName) {
        return Counter.builder("cache.misses")
                .tag("cache", cacheName)
                .description("Number of cache misses")
                .register(meterRegistry);
    }

    private Timer createLatencyTimer(String cacheName) {
        return Timer.builder("cache.latency")
                .tag("cache", cacheName)
                .description("Cache operation latency")
                .register(meterRegistry);
    }
}