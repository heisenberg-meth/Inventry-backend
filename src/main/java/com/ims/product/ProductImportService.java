package com.ims.product;

import com.ims.category.Category;
import com.ims.category.CategoryRepository;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.math.BigDecimal;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
@SuppressWarnings("null")
public class ProductImportService {

    private final ProductRepository productRepository;
    private final CategoryRepository categoryRepository;

    @Transactional
    public Map<String, Object> importProducts(MultipartFile file) {
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
                if (data.length < 3) {
                    errors.add("Line " + lineNum + ": Invalid format (at least Name, SalePrice, Stock required)");
                    failCount++;
                    continue;
                }

                try {
                    String name = data[0].trim();
                    BigDecimal salePrice = new BigDecimal(data[1].trim());
                    int stock = Integer.parseInt(data[2].trim());
                    
                    String sku = data.length > 3 ? data[3].trim() : null;
                    String categoryName = data.length > 4 ? data[4].trim() : "General";

                    // Find or create category
                    Category category = categoryRepository.findAll().stream()
                            .filter(c -> c.getName().equalsIgnoreCase(categoryName))
                            .findFirst()
                            .orElseGet(() -> {
                                Category newCat = Category.builder()
                                        .name(categoryName)
                                        .description("Auto-created during import")
                                        .build();
                                return categoryRepository.save(newCat);
                            });

                    Product product = Product.builder()
                            .name(name)
                            .salePrice(salePrice)
                            .stock(stock)
                            .sku(sku)
                            .categoryId(category.getId())
                            .unit("Unit")
                            .isActive(true)
                            .reorderLevel(10)
                            .build();

                    products.add(product);
                    successCount++;
                } catch (Exception e) {
                    errors.add("Line " + lineNum + ": " + e.getMessage());
                    failCount++;
                }
            }

            productRepository.saveAll(products);

        } catch (Exception e) {
            log.error("Failed to import products", e);
            throw new RuntimeException("Import failed: " + e.getMessage());
        }

        return Map.of(
            "success_count", successCount,
            "fail_count", failCount,
            "errors", errors
        );
    }
}
