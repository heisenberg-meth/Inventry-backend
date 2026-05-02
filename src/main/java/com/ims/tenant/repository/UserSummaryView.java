package com.ims.tenant.repository;

import java.time.LocalDateTime;

/**
 * Interface-based projection for user summary listings.
 * Designed to fetch only the necessary fields for list views in a single query,
 * avoiding N+1 relationship resolution.
 */
public interface UserSummaryView {
  Long getId();

  String getName();

  String getEmail();

  /**
   * Fetched via JOIN in the repository query.
   */
  String getRoleName();

  String getScope();

  Boolean getIsActive();

  LocalDateTime getCreatedAt();
}
