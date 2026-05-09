package com.ims.category;

import com.ims.dto.CategoryRequest;
import com.ims.dto.response.CategoryResponse;
import com.ims.shared.auth.TenantContext;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
import org.springframework.web.bind.annotation.*;

@RestController
@RequestMapping("/api/v1/tenant/categories")
@RequiredArgsConstructor
@Tag(name = "Tenant - Categories")
@SecurityRequirement(name = "bearerAuth")
public class CategoryController {

  private final CategoryService categoryService;
  private final MeterRegistry meterRegistry;

  @GetMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "List categories")
  public ResponseEntity<Page<CategoryResponse>> list(Pageable pageable) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      Page<CategoryResponse> result =
          categoryService.getCategories(pageable).map(categoryService::toResponse);
      meterRegistry.counter("ims.category.list.success").increment();
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      meterRegistry.counter("ims.category.list.error").increment();
      throw e;
    } finally {
      sample.stop(
          Timer.builder("ims.category.list.latency")
              .description("Latency for listing categories")
              .register(meterRegistry));
    }
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Create category")
  public ResponseEntity<CategoryResponse> create(@Valid @RequestBody CategoryRequest request) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      TenantContext.requireTenantId();

      CategoryResponse result = categoryService.toResponse(categoryService.create(request));
      meterRegistry.counter("ims.category.create.success").increment();
      return ResponseEntity.status(HttpStatus.CREATED).body(result);
    } catch (Exception e) {
      meterRegistry.counter("ims.category.create.error").increment();
      throw e;
    } finally {
      sample.stop(
          Timer.builder("ims.category.create.latency")
              .description("Latency for creating categories")
              .register(meterRegistry));
    }
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Get category details")
  public ResponseEntity<CategoryResponse> get(@PathVariable Long id) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      CategoryResponse result = categoryService.toResponse(categoryService.getById(id));
      meterRegistry.counter("ims.category.get.success").increment();
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      meterRegistry.counter("ims.category.get.error").increment();
      throw e;
    } finally {
      sample.stop(
          Timer.builder("ims.category.get.latency")
              .description("Latency for getting category details")
              .register(meterRegistry));
    }
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Update category")
  public ResponseEntity<CategoryResponse> update(
      @PathVariable Long id, @Valid @RequestBody CategoryRequest request) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      CategoryResponse result = categoryService.toResponse(categoryService.update(id, request));
      meterRegistry.counter("ims.category.update.success").increment();
      return ResponseEntity.ok(result);
    } catch (Exception e) {
      meterRegistry.counter("ims.category.update.error").increment();
      throw e;
    } finally {
      sample.stop(
          Timer.builder("ims.category.update.latency")
              .description("Latency for updating categories")
              .register(meterRegistry));
    }
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Delete category")
  public ResponseEntity<Void> delete(@PathVariable Long id) {
    Timer.Sample sample = Timer.start(meterRegistry);
    try {
      categoryService.delete(id);
      meterRegistry.counter("ims.category.delete.success").increment();
      return ResponseEntity.noContent().build();
    } catch (Exception e) {
      meterRegistry.counter("ims.category.delete.error").increment();
      throw e;
    } finally {
      sample.stop(
          Timer.builder("ims.category.delete.latency")
              .description("Latency for deleting categories")
              .register(meterRegistry));
    }
  }
}
