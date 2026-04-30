package com.ims.shared.lock;

import java.time.Duration;
import java.util.concurrent.TimeUnit;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

@Service
@Slf4j
@RequiredArgsConstructor
public class DistributedLockService {

    private final RedisTemplate<String, Object> redisTemplate;
    private static final String LOCK_PREFIX = "lock:";

    /**
     * Tries to acquire a lock.
     * 
     * @param lockKey Unique key for the lock
     * @param timeout Duration before the lock automatically expires (safety net)
     * @return true if lock was acquired, false otherwise
     */
    public boolean acquireLock(String lockKey, Duration timeout) {
        String key = LOCK_PREFIX + lockKey;
        Boolean acquired = redisTemplate.opsForValue().setIfAbsent(key, "locked", timeout);
        boolean result = Boolean.TRUE.equals(acquired);
        if (result) {
            log.trace("Acquired distributed lock for key: {}", key);
        }
        return result;
    }

    /**
     * Releases a lock.
     * 
     * @param lockKey Unique key for the lock
     */
    public void releaseLock(String lockKey) {
        String key = LOCK_PREFIX + lockKey;
        redisTemplate.delete(key);
        log.trace("Released distributed lock for key: {}", key);
    }

    /**
     * Executes a task within a distributed lock.
     * 
     * @param lockKey  Unique key for the lock
     * @param timeout  Safety timeout for the lock
     * @param waitTime Maximum time to wait for the lock
     * @param task     Task to execute
     * @return true if task was executed, false if lock could not be acquired
     */
    public boolean withLock(String lockKey, Duration timeout, Duration waitTime, Runnable task) {
        long start = System.currentTimeMillis();
        long end = start + waitTime.toMillis();

        while (System.currentTimeMillis() < end) {
            if (acquireLock(lockKey, timeout)) {
                try {
                    task.run();
                    return true;
                } finally {
                    releaseLock(lockKey);
                }
            }
            try {
                TimeUnit.MILLISECONDS.sleep(100); // Wait 100ms before retrying
            } catch (InterruptedException e) {
                Thread.currentThread().interrupt();
                return false;
            }
        }
        log.warn("Could not acquire distributed lock for key: {} within {}ms", lockKey, waitTime.toMillis());
        return false;
    }
}
