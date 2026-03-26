package com.ims.tenant.service;

import com.ims.dto.CategoryRequest;
import com.ims.model.Category;
import com.ims.tenant.repository.CategoryRepository;
import com.ims.tenant.repository.ProductRepository;
import jakarta.persistence.EntityNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@RequiredArgsConstructor
public class CategoryService {

  private final CategoryRepository categoryRepository;
  private final ProductRepository productRepository;

  public Page<Category> getCategories(Pageable pageable) {
    return categoryRepository.findAll(pageable);
  }

  public Category getById(Long id) {
    return categoryRepository
        .findById(id)
        .orElseThrow(() -> new EntityNotFoundException("Category not found"));
  }

  @Transactional
  public Category create(CategoryRequest request) {
    if (categoryRepository.existsByNameIgnoreCase(request.getName())) {
      throw new IllegalArgumentException("Category with this name already exists");
    }

    Category category =
        Category.builder()
            .name(request.getName())
            .description(request.getDescription())
            .build();

    return categoryRepository.save(category);
  }

  @Transactional
  public Category update(Long id, CategoryRequest request) {
    Category category = getById(id);

    if (!category.getName().equalsIgnoreCase(request.getName())
        && categoryRepository.existsByNameIgnoreCase(request.getName())) {
      throw new IllegalArgumentException("Category with this name already exists");
    }

    category.setName(request.getName());
    category.setDescription(request.getDescription());

    return categoryRepository.save(category);
  }

  @Transactional
  public void delete(Long id) {
    Category category = getById(id);
    long productCount = productRepository.countByCategoryId(id);

    if (productCount > 0) {
      throw new DataIntegrityViolationException(
          "Category has " + productCount + " products. Reassign before deleting.");
    }

    categoryRepository.delete(category);
  }
}
