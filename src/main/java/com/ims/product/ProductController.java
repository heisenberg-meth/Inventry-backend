package com.ims.product;

import com.ims.dto.request.CreateProductRequest;
import com.ims.dto.response.ProductResponse;
import io.swagger.v3.oas.annotations.Operation;
import io.swagger.v3.oas.annotations.security.SecurityRequirement;
import io.swagger.v3.oas.annotations.tags.Tag;
import jakarta.validation.Valid;
import java.util.List;
import lombok.RequiredArgsConstructor;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.Pageable;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.security.access.prepost.PreAuthorize;
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
@RequestMapping({"/api/v1/products", "/api/tenant/products"})
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
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "List products", description = "Paginated, cached 15min")
  public ResponseEntity<Page<ProductResponse>> getProducts(Pageable pageable) {
    return ResponseEntity.ok(productService.getProducts(pageable));
  }

  @GetMapping("/next")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "List products (Cursor pagination)", description = "High performance, no offset")
  public ResponseEntity<List<ProductResponse>> getNextProducts(
      @RequestParam Long lastId,
      @RequestParam(defaultValue = "20") int limit) {
    return ResponseEntity.ok(productService.getNextProducts(lastId, limit));
  }

  @PostMapping
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Create product")
  public ResponseEntity<ProductResponse> createProduct(
      @Valid @RequestBody CreateProductRequest request) {
    return ResponseEntity.status(HttpStatus.CREATED).body(productService.createProduct(request));
  }

  @GetMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Get product detail")
  public ResponseEntity<ProductResponse> getProduct(@PathVariable Long id) {
    return ResponseEntity.ok(productService.getProductById(id));
  }

  @PutMapping("/{id}")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Update product")
  public ResponseEntity<ProductResponse> updateProduct(
      @PathVariable Long id, @Valid @RequestBody CreateProductRequest request) {
    return ResponseEntity.ok(productService.updateProduct(id, request));
  }

  @PostMapping("/{id}/duplicate")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Clone a product")
  public ResponseEntity<ProductResponse> duplicateProduct(@PathVariable Long id) {
    return ResponseEntity.status(HttpStatus.CREATED).body(productService.duplicateProduct(id));
  }

  @DeleteMapping("/{id}")
  @PreAuthorize("hasAuthority('delete_product')")
  @Operation(summary = "Soft delete product")
  public ResponseEntity<Void> deleteProduct(@PathVariable Long id) {
    productService.deleteProduct(id);
    return ResponseEntity.noContent().build();
  }

  @GetMapping("/low-stock")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Items below reorder level")
  public ResponseEntity<List<ProductResponse>> getLowStock() {
    return ResponseEntity.ok(productService.getLowStockProducts());
  }

  @GetMapping("/expiring")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Pharmacy: products expiring within N days")
  public ResponseEntity<List<ProductResponse>> getExpiring(
      @RequestParam(required = false) Integer days) {
    return ResponseEntity.ok(productService.getExpiringProducts(days));
  }

  @GetMapping("/search")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Search by name/SKU/barcode")
  public ResponseEntity<Page<ProductResponse>> search(@RequestParam String q, Pageable pageable) {
    return ResponseEntity.ok(productService.searchProducts(q, pageable));
  }

  @PostMapping("/bulk-import")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Bulk import products via CSV")
  public ResponseEntity<java.util.Map<String, Object>> bulkImport(
      @RequestParam("file") org.springframework.web.multipart.MultipartFile file) {
    return ResponseEntity.ok(importService.importProducts(file));
  }

  @GetMapping("/export")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER')")
  @Operation(summary = "Export all products as CSV")
  public ResponseEntity<String> exportProducts() {
    var products = productRepository.findByIsDeletedFalse(org.springframework.data.domain.Pageable.unpaged())
        .getContent();

    var data = products.stream().map(p -> {
      java.util.Map<String, Object> map = new java.util.LinkedHashMap<>();
      map.put("ID", p.getId());
      map.put("Name", p.getName());
      map.put("SKU", p.getSku());
      map.put("Stock", p.getStock());
      map.put("Price", p.getSalePrice());
      map.put("CategoryID", p.getCategoryId());
      return map;
    }).collect(java.util.stream.Collectors.toList());

    String csv = csvExportService.exportToCsv(java.util.List.of("ID", "Name", "SKU", "Stock", "Price", "CategoryID"),
        data);

    return ResponseEntity.ok()
        .header(org.springframework.http.HttpHeaders.CONTENT_DISPOSITION, "attachment; filename=products.csv")
        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "text/csv")
        .body(csv);
  }

  @GetMapping("/{id}/barcode")
  @PreAuthorize("hasAnyRole('ADMIN', 'MANAGER', 'STAFF')")
  @Operation(summary = "Generate barcode for product")
  public ResponseEntity<byte[]> getBarcode(@PathVariable Long id) {
    ProductResponse p = productService.getProductById(id);
    byte[] image = barcodeService.generateBarcodeImage(p.getBarcode() != null ? p.getBarcode() : p.getSku());
    return ResponseEntity.ok()
        .header(org.springframework.http.HttpHeaders.CONTENT_TYPE, "image/png")
        .body(image);
  }
}
