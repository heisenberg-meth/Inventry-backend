package com.ims.category;

import static org.assertj.core.api.Assertions.assertThat;

import com.ims.BaseIntegrationTest;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;

class CategoryRepositoryTest extends BaseIntegrationTest {

  @Autowired private CategoryRepository categoryRepository;

  @Test
  void findByIdAndTenantId_ShouldReturnCategory() {
    com.ims.shared.auth.TenantContext.setTenantId(testTenant1Id);
    Category category =
        Category.builder().name("Electronics").taxRate(BigDecimal.valueOf(18)).build();
    category = categoryRepository.save(category);

    Optional<Category> found =
        categoryRepository.findByIdAndTenantId(category.getId(), testTenant1Id);

    assertThat(found).isPresent();
    assertThat(found.get().getName()).isEqualTo("Electronics");
  }

  @Test
  void findByIdAndTenantId_ShouldNotReturnCategory_ForDifferentTenant() {
    com.ims.shared.auth.TenantContext.setTenantId(testTenant1Id);
    Category category = Category.builder().name("Electronics").build();
    category = categoryRepository.save(category);

    Optional<Category> found =
        categoryRepository.findByIdAndTenantId(category.getId(), testTenant2Id);

    assertThat(found).isEmpty();
  }

  @Test
  void findByTenantId_ShouldReturnOnlyTenantCategories() {
    com.ims.shared.auth.TenantContext.setTenantId(testTenant1Id);
    categoryRepository.save(Category.builder().name("C1").build());

    com.ims.shared.auth.TenantContext.setTenantId(testTenant2Id);
    categoryRepository.save(Category.builder().name("C2").build());

    Page<Category> t1Categories =
        categoryRepository.findByTenantId(testTenant1Id, PageRequest.of(0, 10));

    assertThat(t1Categories.getContent()).hasSize(1);
    assertThat(t1Categories.getContent().get(0).getName()).isEqualTo("C1");
  }

  @Test
  void existsByNameIgnoreCaseAndTenantId_ShouldWork() {
    com.ims.shared.auth.TenantContext.setTenantId(testTenant1Id);
    categoryRepository.save(Category.builder().name("Electronics").build());

    assertThat(categoryRepository.existsByNameIgnoreCaseAndTenantId("electronics", testTenant1Id))
        .isTrue();
    assertThat(categoryRepository.existsByNameIgnoreCaseAndTenantId("ELECTRONICS", testTenant1Id))
        .isTrue();
    assertThat(categoryRepository.existsByNameIgnoreCaseAndTenantId("Electronics", testTenant2Id))
        .isFalse();
  }
}
