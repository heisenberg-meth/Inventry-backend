package com.ims.category;

import com.ims.dto.response.CategoryResponse;
import com.ims.dto.response.PagedResponse;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditResource;
import com.ims.dto.CategoryRequest;
import com.ims.shared.rbac.RequiresPermission;
import com.ims.product.ProductRepository;
import com.ims.shared.auth.TenantContext;
import java.util.Objects;
import jakarta.persistence.EntityNotFoundException;
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
  private final com.ims.shared.audit.AuditLogService auditLogService;

  public PagedResponse<CategoryResponse> getCategories(Long tenantId, @NonNull Pageable pageable) {
    if (tenantId == null) {
      log.error("Tenant ID is missing in CategoryService.getCategories");
      throw new IllegalArgumentException("Tenant context is missing");
    }

    Page<CategoryResponse> page = categoryRepository.findByTenantId(tenantId, pageable).map(this::toResponse);
    return new PagedResponse<>(
        page.getContent(),
        page.getTotalElements(),
        page.getTotalPages(),
        page.getNumber(),
        page.getSize()
    );
  }

  public Category getById(@NonNull Long id) {
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("Tenant context is missing");
    }
    return categoryRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Category not found"));
  }

  @Transactional
  public Category create(CategoryRequest request) {
    if (categoryRepository.existsByNameIgnoreCaseAndTenantId(request.getName(), TenantContext.getTenantId())) {
      throw new IllegalArgumentException("Category with this name already exists");
    }

    Category category = Category.builder()
        .tenantId(TenantContext.getTenantId())
        .name(request.getName())
        .description(request.getDescription())
        .taxRate(request.getTaxRate() != null ? request.getTaxRate() : java.math.BigDecimal.ZERO)
        .build();

    Category savedCategory = Objects.requireNonNull(categoryRepository.save(category));

    auditLogService.logAudit(
        AuditAction.CREATE,
        AuditResource.CATEGORY,
        savedCategory.getId(),
        "Created category: " + savedCategory.getName());

    return savedCategory;
  }

  @Transactional
  public Category update(@NonNull Long id, CategoryRequest request) {
    Category category = getById(id);
    Long tenantId = TenantContext.getTenantId();
    if (tenantId == null) {
      throw new IllegalStateException("Tenant context is missing");
    }

    if (!category.getName().equalsIgnoreCase(request.getName())
        && categoryRepository.existsByNameIgnoreCaseAndTenantId(request.getName(), tenantId)) {
      throw new IllegalArgumentException("Category with this name already exists");
    }

    category.setName(request.getName());
    category.setDescription(request.getDescription());
    if (request.getTaxRate() != null) {
      category.setTaxRate(request.getTaxRate());
    }

    Category updatedCategory = Objects.requireNonNull(categoryRepository.save(category));

    auditLogService.logAudit(
        AuditAction.UPDATE,
        AuditResource.CATEGORY,
        updatedCategory.getId(),
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
        AuditAction.DELETE,
        AuditResource.CATEGORY,
        id,
        "Deleted category: " + category.getName());
  }

  public CategoryResponse toResponse(Category category) {
    return CategoryResponse.builder()
        .id(category.getId())
        .name(category.getName())
        .description(category.getDescription())
        .taxRate(category.getTaxRate())
        .createdAt(category.getCreatedAt())
        .build();
  }
}
