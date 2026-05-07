package com.ims.category;

import com.ims.dto.response.CategoryResponse;
import com.ims.shared.audit.AuditAction;
import com.ims.shared.audit.AuditResource;

import com.ims.dto.CategoryRequest;
import com.ims.product.ProductRepository;
import com.ims.shared.auth.TenantContext;
import java.util.Objects;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Transactional
@Service
@RequiredArgsConstructor
@Slf4j
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;
  private final com.ims.shared.audit.AuditLogService auditLogService;
  private final io.micrometer.core.instrument.MeterRegistry meterRegistry;
  private final io.micrometer.observation.ObservationRegistry observationRegistry;

  @Cacheable(cacheResolver = "tenantAwareCacheResolver", value = "categories", key = "'list:' + #pageable.pageNumber + ':' + #pageable.pageSize")
  public Page<Category> getCategories(Pageable pageable) {
    Long tenantId = TenantContext.requireTenantId();

    return categoryRepository.findByTenantId(tenantId, pageable);
  }

  public Category getById(Long id) {
    Long tenantId = TenantContext.requireTenantId();

    return categoryRepository
        .findByIdAndTenantId(id, tenantId)
        .orElseThrow(() -> new EntityNotFoundException("Category not found"));
  }

  @Transactional
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "categories", allEntries = true)
  public Category create(CategoryRequest request) {
    return io.micrometer.observation.Observation.createNotStarted("ims.category.create", observationRegistry)
        .contextualName("create-category")
        .lowCardinalityKeyValue("tenantId", String.valueOf(TenantContext.getTenantId()))
        .observe(() -> {
          if (categoryRepository.existsByNameIgnoreCaseAndTenantId(request.getName(), TenantContext.getTenantId())) {
            throw new IllegalArgumentException("Category with this name already exists");
          }

          Category category = Category.builder()
              .tenantId(TenantContext.getTenantId())
              .name(request.getName())
              .description(request.getDescription())
              .taxRate(request.getTaxRate() != null ? request.getTaxRate() : java.math.BigDecimal.ZERO)
              .build();

          Category savedCategory = categoryRepository.save(category);

          auditLogService.logAudit(
              AuditAction.CREATE,
              AuditResource.CATEGORY,
              savedCategory.getId(),
              "Created category: " + savedCategory.getName());

          // Custom Metric: Category Creation
          meterRegistry.counter("ims.category.created", "tenantId", String.valueOf(TenantContext.getTenantId()))
              .increment();

          return savedCategory;
        });
  }

  @Transactional
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "categories", allEntries = true)
  public Category update(Long id, CategoryRequest request) {
    Category category = getById(id);
    Long tenantId = TenantContext.requireTenantId();

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
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "categories", allEntries = true)
  @PreAuthorize("hasAuthority('delete_category')")
  public void delete(Long id) {
    Category category = getById(id);
    long productCount = productRepository.countByCategoryIdAndTenantId(id, TenantContext.requireTenantId());

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

    // Custom Metric: Category Deletion
    meterRegistry.counter("ims.category.deleted", "tenantId", String.valueOf(TenantContext.getTenantId())).increment();
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
