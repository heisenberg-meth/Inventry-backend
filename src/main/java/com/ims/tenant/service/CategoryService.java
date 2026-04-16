package com.ims.tenant.service;

import com.ims.dto.CategoryRequest;
import com.ims.model.Category;
import com.ims.shared.rbac.RequiresPermission;
import com.ims.tenant.repository.CategoryRepository;
import com.ims.tenant.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;
  private final com.ims.shared.audit.AuditLogService auditLogService;

  @Cacheable(cacheResolver = "tenantAwareCacheResolver", value = "categories", key = "'list:' + #pageable.pageNumber + ':' + #pageable.pageSize")
  public Page<Category> getCategories(@NonNull Pageable pageable) {
    return categoryRepository.findAll(pageable);
  }

  public Category getById(@NonNull Long id) {
    return categoryRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Category not found"));
  }

  @Transactional
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "categories", allEntries = true)
  public Category create(CategoryRequest request) {
    if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
      throw new IllegalArgumentException("Category with this name already exists");
    }

    Category category =
        Category.builder()
            .name(request.getName())
            .description(request.getDescription())
            .taxRate(request.getTaxRate() != null ? request.getTaxRate() : java.math.BigDecimal.ZERO)
            .build();

    Category savedCategory = categoryRepository.save(category);

    auditLogService.logAudit(
        "CREATE",
        "CATEGORY",
        savedCategory.getId(),
        "Created category: " + savedCategory.getName());

    return savedCategory;
  }

  @Transactional
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "categories", allEntries = true)
  public Category update(@NonNull Long id, CategoryRequest request) {
    Category category = getById(id);

    if (!category.getName().equalsIgnoreCase(request.getName())
        && categoryRepository.existsByNameIgnoreCase(request.getName())) {
      throw new IllegalArgumentException("Category with this name already exists");
    }

    category.setName(request.getName());
    category.setDescription(request.getDescription());
    if (request.getTaxRate() != null) {
      category.setTaxRate(request.getTaxRate());
    }

    Category updatedCategory = categoryRepository.save(category);

    auditLogService.logAudit(
        "UPDATE",
        "CATEGORY",
        updatedCategory.getId(),
        "Updated category: " + updatedCategory.getName());

    return updatedCategory;
  }

  @Transactional
  @CacheEvict(cacheResolver = "tenantAwareCacheResolver", value = "categories", allEntries = true)
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
        "DELETE",
        "CATEGORY",
        id,
        "Deleted category: " + category.getName());
  }
}
