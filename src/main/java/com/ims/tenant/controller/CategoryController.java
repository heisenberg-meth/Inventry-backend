package com.ims.tenant.controller;

import com.ims.dto.CategoryRequest;
import com.ims.model.Category;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.CategoryService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import java.util.Objects;
import org.springframework.lang.NonNull;

@RestController
@RequestMapping("/api/tenant/categories")
@RequiredArgsConstructor
@Tag(name = "Tenant - Categories")
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {

  private final CategoryService categoryService;

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "List categories")
  public ResponseEntity<Page<Category>> list(@NonNull Pageable pageable) {
    Long tenantId = com.ims.shared.auth.TenantContext.get();
    return ResponseEntity.ok(categoryService.getCategories(tenantId, Objects.requireNonNull(pageable)));
  }

  @PostMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Create category")
  public ResponseEntity<Category> create(@Valid @RequestBody CategoryRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(categoryService.create(request));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Get category details")
  public ResponseEntity<Category> get(@NonNull @PathVariable Long id) {
    return ResponseEntity.ok(categoryService.getById(Objects.requireNonNull(id)));
  }

  @PutMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Update category")
  public ResponseEntity<Category> update(
      @NonNull @PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
    return ResponseEntity.ok(categoryService.update(Objects.requireNonNull(id), request));
  }

  @DeleteMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Delete category")
  public ResponseEntity<Void> delete(@NonNull @PathVariable Long id) {
    categoryService.delete(Objects.requireNonNull(id));
    return ResponseEntity.noContent().build();
  }
}
