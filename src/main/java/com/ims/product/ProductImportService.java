package com.ims.product;

import com.ims.category.Category;
import com.ims.category.CategoryRepository;
import com.ims.shared.auth.TenantContext;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class ProductImportService {

  // CSV column indexes: Name, SalePrice, Stock, [SKU], [CategoryName]
  private static final int REQUIRED_COLUMN_COUNT = 3;
  private static final int COL_SKU_INDEX = 3;
  private static final int MIN_COLUMNS_FOR_SKU = 3;
  private static final int COL_CATEGORY_INDEX = 4;
  private static final int MIN_COLUMNS_FOR_CATEGORY = 4;
  private static final int DEFAULT_REORDER_LEVEL = 10;

  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;

  @Transactional
  public Map<String, Object> importProducts(MultipartFile file, boolean dryRun) {
    Long tenantId = TenantContext.requireTenantId();

    Map<String, Category> categoryCache = new HashMap<>();
    List<Product> products = new ArrayList<>();
    int successCount = 0;
    int failCount = 0;
    List<String> errors = new ArrayList<>();

    try (BufferedReader reader = new BufferedReader(new InputStreamReader(file.getInputStream()))) {
      String line;
      boolean firstLine = true;
      int lineNum = 0;

      while ((line = reader.readLine()) != null) {
        lineNum++;
        if (firstLine) {
          firstLine = false;
          continue;
        }

        String[] data = line.split(",");
        if (data.length < REQUIRED_COLUMN_COUNT) {
          errors.add(
              "Line " + lineNum + ": Invalid format (at least Name, SalePrice, Stock required)");
          failCount++;
          continue;
        }

        try {
          String name = data[0].trim();
          if (name.isBlank()) {
            throw new IllegalArgumentException("Product name is required");
          }
          BigDecimal salePrice = new BigDecimal(data[1].trim());
          int stock = Integer.parseInt(data[2].trim());

          String rawSku = data.length > MIN_COLUMNS_FOR_SKU ? data[COL_SKU_INDEX].trim() : null;
          String sku = (rawSku == null || rawSku.isBlank()) ? "SKU-" + java.util.UUID.randomUUID().toString().substring(0, 8) : rawSku;
          String categoryName =
              data.length > MIN_COLUMNS_FOR_CATEGORY ? data[COL_CATEGORY_INDEX].trim() : "General";

          if (categoryName.isBlank()) {
            categoryName = "General";
          }

          // Find or create category with caching
          String finalCategoryName = categoryName;
          Category category =
              categoryCache.computeIfAbsent(
                  finalCategoryName.toLowerCase(),
                  nameKey ->
                      categoryRepository
                          .findByNameIgnoreCaseAndTenantId(finalCategoryName, tenantId)
                          .orElseGet(
                              () -> {
                                log.info(
                                    "Creating new category '{}' for tenant {}",
                                    finalCategoryName,
                                    tenantId);
                                Category newCat =
                                    Objects.requireNonNull(
                                        Category.builder()
                                            .name(finalCategoryName)
                                            .tenantId(tenantId)
                                            .description("Auto-created during import")
                                            .build());
                                return Objects.requireNonNull(categoryRepository.save(newCat));
                              }));

          Product product =
              Objects.requireNonNull(
                  Product.builder()
                      .name(name)
                      .tenantId(tenantId)
                      .salePrice(salePrice)
                      .stock(stock)
                      .sku(sku)
                      .categoryId(Objects.requireNonNull(category.getId()))
                      .unit("Unit")
                      .isActive(true)
                      .reorderLevel(DEFAULT_REORDER_LEVEL)
                      .build());

          products.add(product);
          successCount++;
        } catch (Exception e) {
          errors.add("Line " + lineNum + ": " + e.getMessage());
          failCount++;
        }
      }

      if (!errors.isEmpty()) {
        return Objects.requireNonNull(Map.of(
            "success_count", 0, "fail_count", failCount, "errors", errors, "status", "FAILED"));
      }

      if (dryRun) {
        return Objects.requireNonNull(Map.of(
            "success_count",
            successCount,
            "fail_count",
            0,
            "errors",
            new ArrayList<>(),
            "status",
            "DRY_RUN_SUCCESS"));
      }

      if (!products.isEmpty()) {
        try {
          productRepository.saveAll(products);
        } catch (org.springframework.dao.DataIntegrityViolationException e) {
          throw new IllegalStateException("Import failed due to DB constraint", e);
        }
      }

    } catch (Exception e) {
      log.error("Fatal error during product import", e);
      throw new RuntimeException("Import failed: " + e.getMessage());
    }

    return Objects.requireNonNull(Map.of(
        "success_count", successCount, "fail_count", 0, "errors", errors, "status", "SUCCESS"));
  }
}
