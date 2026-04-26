package com.ims.product;

import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.response.PagedResponse;
import com.ims.dto.response.ProductResponse;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.rbac.RequiresPermission;
import com.ims.shared.rbac.RequiresRole;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import java.util.LinkedHashMap;
import java.util.stream.Collectors;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Pageable;
import org.springframework.web.multipart.MultipartFile;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.HttpHeaders;
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
@RequestMapping("/api/v1/tenant/products")
@RequiredArgsConstructor
@Tag(name = "Tenant - Products", description = "Product management")
@SecurityRequirement(name = "bearerAuth")
public class ProductController {

  private final ProductService productService;
  private final ProductImportService importService;
  private final com.ims.shared.utils.CsvExportService csvExportService;
  private final ProductRepository productRepository;
  private final BarcodeGeneratorService barcodeService;

  @GetMapping
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "List products", description = "Paginated")
  public ResponseEntity<PagedResponse<ProductResponse>> getProducts(Pageable pageable) {
    Pageable safePageable = Objects.requireNonNull(pageable);
    return ResponseEntity.ok(productService.getProducts(safePageable));
  }

  @GetMapping("/next")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(
      summary = "List products (Cursor pagination)",
      description = "High performance, no offset")
  public ResponseEntity<List<ProductResponse>> getNextProducts(
      @RequestParam Long lastId, @RequestParam(defaultValue = "20") int limit) {
    Long safeLastId = Objects.requireNonNull(lastId);
    Long tenantId = Objects.requireNonNull(TenantContext.getTenantId());
    return ResponseEntity.ok(productService.getNextProducts(tenantId, safeLastId, limit));
  }

  @PostMapping
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Create product")
  public ResponseEntity<ProductResponse> createProduct(
      @Valid @RequestBody CreateProductRequest request) {
    CreateProductRequest safeRequest = Objects.requireNonNull(request);
    return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(safeRequest));
  }

  @GetMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Get product detail")
  public ResponseEntity<ProductResponse> getProduct(@PathVariable long id) {
    return ResponseEntity.ok(productService.getProductById(id));
  }

  @PutMapping("/{id}")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Update product")
  public ResponseEntity<ProductResponse> updateProduct(
      @PathVariable long id, @Valid @RequestBody CreateProductRequest request) {
    CreateProductRequest safeRequest = Objects.requireNonNull(request);
    return ResponseEntity.ok(productService.updateProduct(id, safeRequest));
  }

  @PostMapping("/{id}/duplicate")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Clone a product")
  public ResponseEntity<ProductResponse> duplicateProduct(@PathVariable long id) {
    return ResponseEntity.status(HttpStatus.CREATED).body(productService.duplicateProduct(id));
  }

  @DeleteMapping("/{id}")
  @RequiresPermission("delete_product")
  @Operation(summary = "Soft delete product")
  public ResponseEntity<Void> deleteProduct(@PathVariable long id) {
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
  public ResponseEntity<PagedResponse<ProductResponse>> search(
      @RequestParam String q, Pageable pageable) {
    String safeQ = Objects.requireNonNull(q);
    Pageable safePageable = Objects.requireNonNull(pageable);
    return ResponseEntity.ok(productService.searchProducts(safeQ, safePageable));
  }

  @PostMapping("/bulk-import")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Bulk import products via CSV")
  public ResponseEntity<Map<String, Object>> bulkImport(
      @RequestParam("file") MultipartFile file,
      @RequestParam(value = "dryRun", defaultValue = "false") boolean dryRun) {
    MultipartFile safeFile = Objects.requireNonNull(file);
    return ResponseEntity.ok(importService.importProducts(safeFile, dryRun));
  }

  @GetMapping("/export")
  @RequiresRole({"ADMIN", "MANAGER"})
  @Operation(summary = "Export all products as CSV")
  public ResponseEntity<String> exportProducts() {
    var products = productRepository.findExportDataByIsActiveTrue();

    var data =
        products.stream()
            .map(
                p -> {
                  Map<String, Object> map = new LinkedHashMap<>();
                  map.put("ID", p.getId());
                  map.put("Name", p.getName());
                  map.put("SKU", p.getSku());
                  map.put("Stock", p.getStock());
                  map.put("Price", p.getSalePrice());
                  map.put("CategoryID", p.getCategoryId());
                  return map;
                })
            .collect(Collectors.toList());

    String csv =
        Objects.requireNonNull(
            csvExportService.exportToCsv(
                List.of("ID", "Name", "SKU", "Stock", "Price", "CategoryID"), data));

    return ResponseEntity.ok()
        .header(
            HttpHeaders.CONTENT_DISPOSITION,
            "attachment; filename=products.csv")
        .header(HttpHeaders.CONTENT_TYPE, "text/csv")
        .body(csv);
  }

  @GetMapping("/{id}/barcode")
  @RequiresRole({"ADMIN", "MANAGER", "STAFF"})
  @Operation(summary = "Generate barcode for product")
  public ResponseEntity<byte[]> getBarcode(@PathVariable long id) {
    ProductResponse p = productService.getProductById(id);
    String barcodeData = p.getBarcode() != null ? p.getBarcode() : p.getSku();
    byte[] image =
        Objects.requireNonNull(
            barcodeService.generateBarcodeImage(Objects.requireNonNull(barcodeData)));
    return ResponseEntity.ok()
        .header(HttpHeaders.CONTENT_TYPE, "image/png")
        .body(image);
  }
}
