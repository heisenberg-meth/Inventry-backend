package com.ims.category;

import com.ims.dto.CategoryRequest;
import com.ims.dto.response.CategoryResponse;
import com.ims.dto.response.PagedResponse;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.rbac.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.lang.NonNull;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/tenant/categories")
@RequiredArgsConstructor
@Tag(name = "Tenant - Categories")
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {

  private final CategoryService categoryService;

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "List categories")
  public ResponseEntity<PagedResponse<CategoryResponse>> list(@NonNull Pageable pageable) {
    Long tenantId = TenantContext.getTenantId();
    return ResponseEntity.ok(categoryService.getCategories(tenantId, pageable));
  }

  @PostMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Create category")
  public ResponseEntity<CategoryResponse> create(
      @Valid @RequestBody @NonNull CategoryRequest request) {
    TenantContext.assertTenantPresent();
    return ResponseEntity.status(HttpStatus.CREATED)
        .body(categoryService.toResponse(categoryService.create(request)));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Get category details")
  public ResponseEntity<CategoryResponse> get(@NonNull @PathVariable Long id) {
    return ResponseEntity.ok(categoryService.toResponse(categoryService.getById(id)));
  }

  @PutMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Update category")
  public ResponseEntity<CategoryResponse> update(
      @NonNull @PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
    return ResponseEntity.ok(categoryService.toResponse(categoryService.update(id, request)));
  }

  @DeleteMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Delete category")
  public ResponseEntity<Void> delete(@NonNull @PathVariable Long id) {
    categoryService.delete(id);
    return ResponseEntity.noContent().build();
  }
}
