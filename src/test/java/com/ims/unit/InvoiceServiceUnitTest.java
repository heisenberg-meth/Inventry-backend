package com.ims.unit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import com.ims.dto.CreateInvoiceRequest;
import com.ims.model.Invoice;
import com.ims.model.Order;
import com.ims.model.Tenant;
import com.ims.order.entity.OrderType;
import com.ims.platform.repository.TenantRepository;
import com.ims.product.ProductRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.outbox.OutboxService;
import com.ims.shared.pdf.PdfService;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.service.InvoiceService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.mockito.junit.jupiter.MockitoSettings;
import org.mockito.quality.Strictness;

@ExtendWith(MockitoExtension.class)
@MockitoSettings(strictness = Strictness.LENIENT)
public class InvoiceServiceUnitTest {

  @Mock
  private InvoiceRepository invoiceRepository;
  @Mock
  private OrderItemRepository orderItemRepository;
  @Mock
  private ProductRepository productRepository;
  @Mock
  private TenantRepository tenantRepository;
  @Mock
  private OrderRepository orderRepository;
  @Mock
  private CustomerRepository customerRepository;
  @Mock
  private PdfService pdfService;
  @Mock
  private OutboxService outboxService;
  private MeterRegistry meterRegistry = new SimpleMeterRegistry();

  private InvoiceService invoiceService;

  @BeforeEach
  void setUp() {
    invoiceService = new InvoiceService(
        invoiceRepository,
        orderItemRepository,
        productRepository,
        tenantRepository,
        orderRepository,
        customerRepository,
        pdfService,
        outboxService,
        meterRegistry);
    TenantContext.setTenantId(1L);
  }

  @Test
  void createManual_withValidSaleOrder_createsInvoice() {
    Tenant tenant = Tenant.builder().id(1L).invoiceSequence(0).build();

    Order order = Order.builder()
        .id(1L)
        .type(OrderType.SALE)
        .tenantId(1L)
        .customerId(1L)
        .totalAmount(new BigDecimal("100.00"))
        .taxAmount(new BigDecimal("10.00"))
        .discount(new BigDecimal("0.00"))
        .build();

    when(orderRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(order));
    when(invoiceRepository.existsByTenantIdAndOrderId(1L, 1L)).thenReturn(false);
    when(tenantRepository.findByIdWithLock(1L)).thenReturn(Optional.of(tenant));
    when(invoiceRepository.save(any(Invoice.class)))
        .thenAnswer(
            i -> {
              Invoice inv = i.getArgument(0);
              inv.setId(1L);
              return inv;
            });

    CreateInvoiceRequest request = new CreateInvoiceRequest();
    request.setOrderId(1L);
    Invoice result = invoiceService.createManual(request);

    assertNotNull(result);
    assertEquals(order.getId(), result.getOrderId());
    assertEquals(order.getTotalAmount(), result.getAmount());
    verify(invoiceRepository).save(any(Invoice.class));
  }

  @Test
  void createManual_withNonSaleOrder_throwsException() {
    Order order = Order.builder().id(1L).type(OrderType.PURCHASE).build();

    CreateInvoiceRequest request = new CreateInvoiceRequest();
    request.setOrderId(1L);

    when(orderRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(order));

    assertThrows(IllegalArgumentException.class, () -> invoiceService.createManual(request));
  }
}
