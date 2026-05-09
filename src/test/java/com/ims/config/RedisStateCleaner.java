package com.ims.config;

/**
 * Interface for cleaning Redis state in tests.
 * This is provided as a mock in TestRedisConfig for the 'test' profile.
 */
public interface RedisStateCleaner {
  void clear();
}
