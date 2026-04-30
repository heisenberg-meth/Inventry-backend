package com.ims.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class TenantBuilderTest {

  @Test
  void shouldThrowExceptionOnDuplicateStatus() {
    Tenant.TenantBuilder builder = Tenant.builder().name("Test Tenant").status(TenantStatus.ACTIVE);

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> {
          builder.status(TenantStatus.INACTIVE);
        });

    assertEquals("Status already set to: ACTIVE", exception.getMessage());
  }

  @Test
  void shouldAllowSettingStatusOnce() {
    Tenant tenant = Tenant.builder().name("Test Tenant").status(TenantStatus.ACTIVE).build();

    assertEquals(TenantStatus.ACTIVE, tenant.getStatus());
  }
}
