package com.ims.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;
import java.math.BigDecimal;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import com.ims.dto.CreateInvoiceRequest;
import com.ims.model.Invoice;
import com.ims.model.Order;
import com.ims.model.OrderItem;
import com.ims.model.Tenant;
import com.ims.platform.repository.TenantRepository;
import com.ims.product.ProductRepository;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.pdf.PdfService;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.OrderItemRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.service.InvoiceService;
import jakarta.persistence.EntityNotFoundException;

@ExtendWith(MockitoExtension.class)
class InvoiceServiceUnitTest {

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
                pdfService);
    }

    @Test
    void createManual_withValidSaleOrder_createsInvoice() {
        Tenant tenant = Tenant.builder()
                .id(1L)
                .invoiceSequence(0)
                .build();

        Order order = Order.builder()
                .id(1L)
                .type("SALE")
                .tenantId(1L)
                .customerId(1L)
                .totalAmount(new BigDecimal("100.00"))
                .build();

        OrderItem item = OrderItem.builder()
                .id(1L)
                .orderId(1L)
                .quantity(2)
                .unitPrice(new BigDecimal("50.00"))
                .build();

        CreateInvoiceRequest request = new CreateInvoiceRequest();
        request.setOrderId(1L);

        TenantContext.setTenantId(1L);
        try {
            when(orderRepository.findById(1L)).thenReturn(Optional.of(order));
            when(tenantRepository.findById(1L)).thenReturn(Optional.of(tenant));
            when(tenantRepository.save(any(Tenant.class))).thenAnswer(i -> i.getArgument(0));
            when(invoiceRepository.save(any(Invoice.class))).thenAnswer(i -> i.getArgument(0));

            Invoice result = invoiceService.createManual(request);

            assertNotNull(result);
            verify(invoiceRepository).save(any(Invoice.class));
            verify(tenantRepository).save(any(Tenant.class));
        } finally {
            TenantContext.clear();
        }
    }

    @Test
    void createManual_withNonSaleOrder_throwsException() {
        Order order = Order.builder()
                .id(1L)
                .type("PURCHASE")
                .build();

        CreateInvoiceRequest request = new CreateInvoiceRequest();
        request.setOrderId(1L);

        when(orderRepository.findById(1L)).thenReturn(Optional.of(order));

        assertThrows(IllegalArgumentException.class, () -> {
            invoiceService.createManual(request);
        });
    }

    @Test
    void createManual_withNonExistentOrder_throwsException() {
        CreateInvoiceRequest request = new CreateInvoiceRequest();
        request.setOrderId(999L);

        when(orderRepository.findById(999L)).thenReturn(Optional.empty());

        assertThrows(EntityNotFoundException.class, () -> {
            invoiceService.createManual(request);
        });
    }
}