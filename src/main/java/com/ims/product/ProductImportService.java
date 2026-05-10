package com.ims.product;

import com.ims.category.Category;
import com.ims.category.CategoryRepository;
import com.ims.shared.service.BaseImportService;
import java.math.BigDecimal;
import java.util.ArrayList;
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
public class ProductImportService extends BaseImportService {

  private final ProductRepository productRepository;
  private final CategoryRepository categoryRepository;

  @Transactional
  public Map<String, Object> importProducts(MultipartFile file) {
    List<Product> products = new ArrayList<>();

    Map<String, Object> result =
        executeImport(
            file,
            data -> {
              if (data.length < 3) {
                throw new IllegalArgumentException(
                    "Invalid format (at least Name, SalePrice, Stock required)");
              }

              String name = data[0].trim();
              BigDecimal salePrice = new BigDecimal(data[1].trim());
              int stock = Integer.parseInt(data[2].trim());

              String sku = data.length > 3 ? data[3].trim() : null;
              String categoryName = data.length > 4 ? data[4].trim() : "General";

              // Find or create category
              Category category =
                  categoryRepository.findAll().stream()
                      .filter(c -> c.getName().equalsIgnoreCase(categoryName))
                      .findFirst()
                      .orElseGet(
                          () -> {
                            Category newCat =
                                Category.builder()
                                    .name(categoryName)
                                    .description("Auto-created during import")
                                    .build();
                            return Objects.requireNonNull(
                                categoryRepository.save(newCat), "Saved category must not be null");
                          });

              Product product =
                  Product.builder()
                      .name(name)
                      .salePrice(salePrice)
                      .stock(stock)
                      .sku(sku)
                      .categoryId(category.getId())
                      .unit("Unit")
                      .isDeleted(false)
                      .reorderLevel(10)
                      .build();

              products.add(product);
            });

    Objects.requireNonNull(productRepository.saveAll(products), "Saved products must not be null");

    return result;
  }
}
