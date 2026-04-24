package com.ims.tenant;

import static org.assertj.core.api.Assertions.assertThat;

import com.ims.BaseIntegrationTest;
import com.ims.dto.CreateInvoiceRequest;
import com.ims.model.Order;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.service.InvoiceService;
import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

@SpringBootTest(
    properties = {
      "spring.autoconfigure.exclude=org.springframework.boot.autoconfigure.data.redis.RedisAutoConfiguration,org.springframework.boot.autoconfigure.data.redis.RedisReactiveAutoConfiguration",
      "spring.cache.type=none"
    })
@ActiveProfiles("test")
public class InvoiceConcurrencyTest extends BaseIntegrationTest {

  @Autowired private InvoiceService invoiceService;

  @Autowired private InvoiceRepository invoiceRepository;

  @Autowired private TenantRepository tenantRepository;

  @Autowired private OrderRepository orderRepository;

  private Long tenantId;
  private List<Long> orderIds = new ArrayList<>();

  @BeforeEach
  void setup() {
    cleanupDatabase();
    mockRedisAndCache();

    // Create a tenant
    Tenant tenant =
        Tenant.builder()
            .name("Concurrency Corp")
            .workspaceSlug("concurrency-corp")
            .companyCode("CONC001")
            .businessType("RETAIL")
            .invoiceSequence(0)
            .build();
    tenant = tenantRepository.save(tenant);
    this.tenantId = tenant.getId();

    // Create 10 orders to generate invoices for
    for (int i = 0; i < 10; i++) {
      Order order =
          Order.builder()
              .tenantId(tenantId)
              .type("SALE")
              .status(com.ims.model.OrderStatus.PENDING)
              .totalAmount(new BigDecimal("100.00"))
              .taxAmount(new BigDecimal("10.00"))
              .discount(BigDecimal.ZERO)
              .createdAt(LocalDateTime.now())
              .build();
      order = orderRepository.save(order);
      orderIds.add(order.getId());
    }
  }

  @Test
  void testConcurrentInvoiceGeneration() throws Exception {
    int numberOfThreads = 10;
    ExecutorService executor = Executors.newFixedThreadPool(numberOfThreads);
    Set<String> generatedInvoiceNumbers = Collections.newSetFromMap(new ConcurrentHashMap<>());
    List<CompletableFuture<Void>> futures = new ArrayList<>();

    for (int i = 0; i < numberOfThreads; i++) {
      final int index = i;
      futures.add(
          CompletableFuture.runAsync(
              () -> {
                try {
                  TenantContext.setTenantId(tenantId);
                  CreateInvoiceRequest request = new CreateInvoiceRequest();
                  request.setOrderId(orderIds.get(index));

                  var invoice = invoiceService.createManual(request);
                  generatedInvoiceNumbers.add(invoice.getInvoiceNumber());
                } finally {
                  TenantContext.clear();
                }
              },
              executor));
    }

    CompletableFuture.allOf(futures.toArray(new CompletableFuture<?>[0])).join();
    executor.shutdown();
    executor.awaitTermination(5, TimeUnit.SECONDS);

    // Verify that 10 unique invoice numbers were generated
    assertThat(generatedInvoiceNumbers).hasSize(numberOfThreads);

    // Verify that the sequence in the database is correct
    Tenant tenant = tenantRepository.findById(tenantId).orElseThrow();
    assertThat(tenant.getInvoiceSequence()).isEqualTo(numberOfThreads);

    // Verify that all invoice numbers follow the expected pattern and are unique
    List<String> sortedNumbers = new ArrayList<>(generatedInvoiceNumbers);
    Collections.sort(sortedNumbers);

    for (int i = 1; i <= numberOfThreads; i++) {
      String expectedSuffix = String.format("%04d", i);
      assertThat(sortedNumbers.get(i - 1)).contains(expectedSuffix);
    }
  }
}
