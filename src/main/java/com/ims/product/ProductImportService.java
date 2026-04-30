package com.ims.product;

import com.ims.category.Category;
import com.ims.category.CategoryRepository;
import com.ims.shared.auth.SecurityContextAccessor;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Propagation;
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
  private final SecurityContextAccessor securityContextAccessor;

  private static final int BATCH_SIZE = 100;

  /**
   * Main import logic. No longer globally transactional to allow partial success.
   */
  public Map<String, Object> importProducts(MultipartFile file, boolean dryRun) {
    Long tenantId = securityContextAccessor.requireTenantId();

    Map<String, Category> categoryCache = new HashMap<>();
    categoryRepository.findAll().forEach(cat -> 
        categoryCache.put(cat.getName().toLowerCase(), cat));

    List<Product> chunk = new ArrayList<>();
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

        try {
          String[] data = line.split(",");
          if (data.length < REQUIRED_COLUMN_COUNT) {
            throw new IllegalArgumentException("Invalid format: at least Name, SalePrice, Stock required");
          }

          String name = data[0].trim();
          BigDecimal salePrice = new BigDecimal(data[1].trim());
          int stock = Integer.parseInt(data[2].trim());

          String rawSku = data.length > MIN_COLUMNS_FOR_SKU ? data[COL_SKU_INDEX].trim() : null;
          String sku = (rawSku == null || rawSku.isBlank()) ? "SKU-" + java.util.UUID.randomUUID().toString().substring(0, 8) : rawSku;
          String categoryName = data.length > MIN_COLUMNS_FOR_CATEGORY ? data[COL_CATEGORY_INDEX].trim() : "General";

          // Find or create category in a separate transaction if needed
          Category category = getOrCreateCategory(categoryCache, categoryName, tenantId);

          Product product = Product.builder()
              .name(name).tenantId(tenantId).salePrice(salePrice).stock(stock).sku(sku)
              .categoryId(category.getId()).unit("Unit").active(true).reorderLevel(DEFAULT_REORDER_LEVEL)
              .build();

          if (!dryRun) {
            chunk.add(product);
            if (chunk.size() >= BATCH_SIZE) {
              saveChunk(chunk);
              chunk.clear();
            }
          }
          successCount++;
        } catch (Exception e) {
          errors.add("Line " + lineNum + ": " + e.getMessage());
          failCount++;
        }
      }

      // Final partial chunk
      if (!dryRun && !chunk.isEmpty()) {
        saveChunk(chunk);
      }

    } catch (Exception e) {
      log.error("Fatal error during product import", e);
      return Map.of("status", "FATAL_ERROR", "message", e.getMessage(), "success_count", successCount);
    }

    String status = failCount == 0 ? (dryRun ? "DRY_RUN_SUCCESS" : "SUCCESS") : "PARTIAL_SUCCESS";
    return Map.of(
        "success_count", successCount,
        "fail_count", failCount,
        "errors", errors,
        "status", status
    );
  }

  @Transactional(propagation = Propagation.REQUIRES_NEW)
  public void saveChunk(List<Product> products) {
    try {
      productRepository.saveAll(products);
    } catch (Exception e) {
      log.error("Failed to save chunk of products", e);
      throw new RuntimeException("Chunk save failed: " + e.getMessage());
    }
  }

  @Transactional(propagation = Propagation.REQUIRED)
  protected Category getOrCreateCategory(Map<String, Category> cache, String name, Long tenantId) {
    String nameKey = name.trim().toLowerCase();
    if (cache.containsKey(nameKey)) {
      return cache.get(nameKey);
    }

    Category newCat = Category.builder()
        .name(name.trim()).tenantId(tenantId).description("Auto-created during import").build();
    Category saved = categoryRepository.save(newCat);
    cache.put(nameKey, saved);
    return saved;
  }
}
