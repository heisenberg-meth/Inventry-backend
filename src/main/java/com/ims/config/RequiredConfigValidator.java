package com.ims.config;

import jakarta.annotation.PostConstruct;
import java.util.ArrayList;
import java.util.List;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.env.Environment;

@Configuration
public class RequiredConfigValidator {

  private static final Logger log = LoggerFactory.getLogger(RequiredConfigValidator.class);
  private static final String PLACEHOLDER_PREFIX = "PLEASE_SET";

  private final Environment environment;

  public RequiredConfigValidator(Environment environment) {
    this.environment = environment;
  }

  @PostConstruct
  public void validateRequiredConfigs() {
    List<String> missingConfigs = new ArrayList<>();

    String dbUsername = environment.getProperty("spring.datasource.username");
    String dbPassword = environment.getProperty("spring.datasource.password");
    String jwtSecret = environment.getProperty("app.jwt.secret");
    String redisPassword = environment.getProperty("spring.data.redis.password");
    String kafkaBootstrap = environment.getProperty("spring.kafka.bootstrap-servers");

    if (containsPlaceholder(dbUsername)) {
      missingConfigs.add("DB_USERNAME");
    }
    if (containsPlaceholder(dbPassword)) {
      missingConfigs.add("DB_PASSWORD");
    }
    if (containsPlaceholder(jwtSecret)) {
      missingConfigs.add("JWT_SECRET");
    }
    if (containsPlaceholder(redisPassword)) {
      missingConfigs.add("REDIS_PASSWORD");
    }
    if (containsPlaceholder(kafkaBootstrap)) {
      missingConfigs.add("KAFKA_BOOTSTRAP_SERVERS");
    }

    String activeProfile = environment.getActiveProfiles()[0];
    boolean isProduction = "prod".equalsIgnoreCase(activeProfile);

    if (isProduction && !missingConfigs.isEmpty()) {
      log.error(
          "FATAL: Production startup aborted. Missing required configuration: {}", missingConfigs);
      throw new IllegalStateException(
          "Required environment variables not configured for production: " + missingConfigs);
    }

    if (!missingConfigs.isEmpty()) {
      log.warn(
          "WARNING: Some required configurations are using placeholder values: {}. "
              + "These MUST be configured before production deployment.",
          missingConfigs);
    } else {
      log.info("All required configurations validated successfully");
    }
  }

  private boolean containsPlaceholder(String value) {
    return value != null && value.contains(PLACEHOLDER_PREFIX);
  }
}
