package com.ims.model;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;

import org.junit.jupiter.api.Test;

class UserBuilderTest {

  @Test
  void shouldThrowExceptionOnDuplicateRole() {
    User.UserBuilder builder = User.builder().name("Test User").role(UserRole.ADMIN);

    IllegalStateException exception = assertThrows(
        IllegalStateException.class,
        () -> {
          builder.role(UserRole.MANAGER);
        });

    assertEquals("Role already set to: ADMIN", exception.getMessage());
  }

  @Test
  void shouldAllowSettingRoleOnce() {
    User user = User.builder().name("Test User").role(UserRole.ADMIN).build();

    assertNotNull(user.getRole());
    assertEquals("ADMIN", user.getRole().getName());
  }
}
