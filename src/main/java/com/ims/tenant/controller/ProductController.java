package com.ims.tenant.controller;

import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.response.ProductResponse;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.rbac.RequiresRole;
import com.ims.tenant.service.ProductService;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.lang.NonNull;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/tenant/products")
@RequiredArgsConstructor
@Tag(name = "Tenant - Products", description = "Product management")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

  private final ProductService productService;

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "List products", description = "Paginated, cached 15min")
  public ResponseEntity<Page<ProductResponse>> getProducts(Pageable pageable) {
    Long tenantId = TenantContext.get();
    return ResponseEntity.ok(productService.getProducts(tenantId, pageable));
  }

  @PostMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Create product")
  public ResponseEntity<ProductResponse> createProduct(
      @Valid @RequestBody CreateProductRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Get product detail")
  public ResponseEntity<ProductResponse> getProduct(@NonNull @PathVariable Long id) {
    return ResponseEntity.ok(productService.getProductById(id));
  }

  @PutMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Update product")
  public ResponseEntity<ProductResponse> updateProduct(
      @NonNull @PathVariable Long id, @Valid @RequestBody CreateProductRequest request) {
    return ResponseEntity.ok(productService.updateProduct(id, request));
  }

  @DeleteMapping("/{id}")
  @RequiresRole({"ADMIN"})
  @Operation(summary = "Soft delete product")
  public ResponseEntity<Void> deleteProduct(@NonNull @PathVariable Long id) {
    productService.deleteProduct(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/low-stock")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Items below reorder level")
  public ResponseEntity<List<ProductResponse>> getLowStock() {
    return ResponseEntity.ok(productService.getLowStockProducts());
  }

  @GetMapping("/expiring")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Pharmacy: products expiring within N days")
  public ResponseEntity<List<ProductResponse>> getExpiring(
      @RequestParam(required = false) Integer days) {
    return ResponseEntity.ok(productService.getExpiringProducts(days));
  }

  @GetMapping("/search")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Search by name/SKU/barcode")
  public ResponseEntity<Page<ProductResponse>> search(@RequestParam String q, Pageable pageable) {
    return ResponseEntity.ok(productService.searchProducts(q, pageable));
  }
}
