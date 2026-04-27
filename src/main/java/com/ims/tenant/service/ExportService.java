package com.ims.tenant.service;

import com.ims.product.Product;
import com.ims.product.ProductRepository;
import com.ims.category.Category;
import com.ims.category.CategoryRepository;
import com.ims.shared.auth.TenantContext;
import org.springframework.data.domain.Pageable;
import java.util.Map;
import java.util.stream.Collectors;
import jakarta.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.PrintWriter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

@Service
@Slf4j
@RequiredArgsConstructor
public class ExportService {

    private final ProductRepository productRepository;
    private final com.ims.tenant.repository.OrderRepository orderRepository;
    private final CategoryRepository categoryRepository;

    /**
     * Streams the entire product catalog for a tenant as a CSV file.
     * Uses paging to ensure low memory footprint even for millions of products.
     */
    @Transactional(readOnly = true)
    public void exportProductsCsv(HttpServletResponse response) throws IOException {
        Long tenantId = TenantContext.requireTenantId();
        
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=products_" + tenantId + ".csv");
        
        // Fetch all categories for this tenant to provide names in the CSV
        Map<Long, String> categoryMap = categoryRepository.findAll(Pageable.unpaged())
            .getContent().stream()
            .collect(Collectors.toMap(Category::getId, Category::getName));
        
        try (PrintWriter writer = response.getWriter()) {
            // Write Header
            writer.println("ID,Name,SKU,Barcode,Stock,Unit,Sale Price,Purchase Price,Category");
            
            int pageNum = 0;
            int pageSize = 500;
            Page<Product> page;
            
            do {
                page = productRepository.findAllByIsActiveTrue(PageRequest.of(pageNum, pageSize));
                for (var product : page.getContent()) {
                    writer.printf("%d,\"%s\",\"%s\",\"%s\",%d,\"%s\",%.2f,%.2f,\"%s\"%n",
                        product.getId(),
                        escapeCsv(product.getName()),
                        escapeCsv(product.getSku()),
                        escapeCsv(product.getBarcode()),
                        product.getStock(),
                        escapeCsv(product.getUnit()),
                        product.getSalePrice(),
                        product.getPurchasePrice(),
                        product.getCategoryId() != null ? escapeCsv(categoryMap.getOrDefault(product.getCategoryId(), "Unknown")) : ""
                    );
                }
                writer.flush(); // Send to browser immediately
                pageNum++;
            } while (page.hasNext());
        }
    }

    /**
     * Streams orders for a tenant as a CSV file based on type and date range.
     */
    @Transactional(readOnly = true)
    public void exportOrdersCsv(HttpServletResponse response, String type, java.time.LocalDateTime from, java.time.LocalDateTime to) throws IOException {
        
        response.setContentType("text/csv");
        response.setHeader("Content-Disposition", "attachment; filename=orders_" + (type != null ? type : "all") + ".csv");
        
        try (PrintWriter writer = response.getWriter()) {
            writer.println("Order ID,Type,Status,Customer/Supplier,Total Amount,Tax Amount,Created At");
            
            int pageNum = 0;
            int pageSize = 500;
            Page<com.ims.model.Order> page;
            
            do {
                if (type != null) {
                    page = orderRepository.findByTypeAndDateRange(type, from, to, PageRequest.of(pageNum, pageSize));
                } else {
                    page = orderRepository.findByDateRange(from, to, PageRequest.of(pageNum, pageSize));
                }

                for (var order : page.getContent()) {
                    String party = order.getCustomerId() != null ? "Customer:" + order.getCustomerId() : 
                                  (order.getSupplierId() != null ? "Supplier:" + order.getSupplierId() : "N/A");
                    
                    writer.printf("%d,\"%s\",\"%s\",\"%s\",%.2f,%.2f,\"%s\"%n",
                        order.getId(),
                        order.getType(),
                        order.getStatus(),
                        party,
                        order.getTotalAmount(),
                        order.getTaxAmount(),
                        order.getCreatedAt()
                    );
                }
                writer.flush();
                pageNum++;
            } while (page.hasNext());
        }
    }

    private String escapeCsv(String value) {
        if (value == null) return "";
        return value.replace("\"", "\"\"");
    }
}
