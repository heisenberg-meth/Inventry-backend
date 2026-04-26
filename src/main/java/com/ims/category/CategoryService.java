package com.ims.category;

import com.ims.dto.CategoryRequest;
import com.ims.dto.response.CategoryResponse;
import com.ims.dto.response.PagedResponse;
import com.ims.product.ProductRepository;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditLogService;
import com.ims.shared.audit.AuditResource;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.rbac.RequiresPermission;
import jakarta.persistence.EntityNotFoundException;
import java.math.BigDecimal;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;
  private final AuditLogService auditLogService;

  public @NonNull PagedResponse<CategoryResponse> getCategories(
      @NonNull Long tenantId, @NonNull Pageable pageable) {
    TenantContext.assertTenantPresent();

    Page<CategoryResponse> page =
        categoryRepository.findByTenantId(tenantId, pageable).map(this::toResponse);
    return new PagedResponse<>(
        page.getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize());
  }

  public @NonNull Category getById(@NonNull Long id) {
    TenantContext.assertTenantPresent();
    Long tenantId = TenantContext.getTenantId();
    return Objects.requireNonNull(
    categoryRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Category not found"))
    );
    return java.util.Objects.requireNonNull(
        categoryRepository
            .findByIdAndTenantId(id, tenantId)
            .orElseThrow(() -> new EntityNotFoundException("Category not found")));
  }

  @Transactional
  public @NonNull Category create(@NonNull CategoryRequest request) {
    String name = Objects.requireNonNull(request.getName());

    if (categoryRepository.existsByNameIgnoreCaseAndTenantId(name, TenantContext.getTenantId())) {
      throw new IllegalArgumentException("Category with this name already exists");
    }

    Category category =
        Objects.requireNonNull(
            Category.builder()
            .name(name)
            .description(request.getDescription())
            .taxRate(request.getTaxRate() != null ? request.getTaxRate() : BigDecimal.ZERO)
            .build());

    Category savedCategory = Objects.requireNonNull(categoryRepository.save(category));

    auditLogService.logAudit(
        AuditAction.CREATE,
        AuditResource.CATEGORY,
        Objects.requireNonNull(savedCategory.getId()),
        "Created category: " + savedCategory.getName());

    return savedCategory;
  }

  @Transactional
  public @NonNull Category update(@NonNull Long id, @NonNull CategoryRequest request) {

    Category category = getById(id);
    TenantContext.assertTenantPresent();
    Long tenantId = TenantContext.getTenantId();

    String name = Objects.requireNonNull(request.getName());

    if (!category.getName().equalsIgnoreCase(name)
        && categoryRepository.existsByNameIgnoreCaseAndTenantId(name, tenantId)) {
      throw new IllegalArgumentException("Category with this name already exists");
    }

    category.setName(name);
    category.setDescription(request.getDescription());

    if (request.getTaxRate() != null) {
      category.setTaxRate(request.getTaxRate());
    }

    Category updatedCategory = Objects.requireNonNull(categoryRepository.save(category));

    auditLogService.logAudit(
        AuditAction.UPDATE,
        AuditResource.CATEGORY,
        Objects.requireNonNull(updatedCategory.getId()),
        "Updated category: " + updatedCategory.getName());

    return updatedCategory;
  }

  @Transactional
  @RequiresPermission("delete_category")
  public void delete(@NonNull Long id) {
    Category category = getById(id);
    long productCount = productRepository.countByCategoryId(id);

    if (productCount > 0) {
      throw new DataIntegrityViolationException(
          "Category has " + productCount + " products. Reassign before deleting.");
    }

    categoryRepository.delete(category);

    auditLogService.logAudit(
        AuditAction.DELETE, AuditResource.CATEGORY, id, "Deleted category: " + category.getName());
  }

  public @NonNull CategoryResponse toResponse(@NonNull Category category) {
    return Objects.requireNonNull(
    CategoryResponse.builder()
        .id(category.getId())
        .name(category.getName())
        .description(category.getDescription())
        .taxRate(category.getTaxRate())
        .createdAt(category.getCreatedAt())
        .build()
    );
    return java.util.Objects.requireNonNull(
        CategoryResponse.builder()
            .id(category.getId())
            .name(category.getName())
            .description(category.getDescription())
            .taxRate(category.getTaxRate())
            .createdAt(category.getCreatedAt())
            .build());
  }
}
