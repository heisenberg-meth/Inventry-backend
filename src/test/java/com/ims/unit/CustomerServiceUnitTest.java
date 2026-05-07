package com.ims.unit;

import static org.junit.jupiter.api.Assertions.*;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.*;
import java.util.Optional;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageImpl;
import org.springframework.data.domain.PageRequest;
import com.ims.model.Customer;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.tenant.repository.InvoiceRepository;
import com.ims.tenant.repository.OrderRepository;
import com.ims.tenant.repository.PaymentRepository;
import com.ims.tenant.service.CustomerService;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;
import com.ims.shared.exception.ResourceNotFoundException;

@ExtendWith(MockitoExtension.class)
class CustomerServiceUnitTest {

    @Mock
    private CustomerRepository customerRepository;
    @Mock
    private OrderRepository orderRepository;
    @Mock
    private InvoiceRepository invoiceRepository;
    @Mock
    private PaymentRepository paymentRepository;
    private MeterRegistry meterRegistry = new SimpleMeterRegistry();

    private CustomerService customerService;

    @BeforeEach
    void setUp() {
        customerService = new CustomerService(
                customerRepository,
                orderRepository,
                invoiceRepository,
                paymentRepository,
                meterRegistry);
        TenantContext.setTenantId(1L);
    }

    @Test
    void getCustomers_returnsPagedResults() {
        Customer customer = Customer.builder()
                .id(1L)
                .name("Test Customer")
                .tenantId(1L)
                .build();
        Page<Customer> page = new PageImpl<>(java.util.List.of(customer));

        when(customerRepository.findAllByTenantId(eq(1L), any(PageRequest.class))).thenReturn(page);

        Page<Customer> result = customerService.getCustomers(PageRequest.of(0, 10));

        assertNotNull(result);
        assertEquals(1, result.getTotalElements());
        assertEquals("Test Customer", result.getContent().get(0).getName());
    }

    @Test
    void getById_existingCustomer_returnsCustomer() {
        Customer customer = Customer.builder()
                .id(1L)
                .name("Test Customer")
                .tenantId(1L)
                .build();

        when(customerRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(customer));

        Customer result = customerService.getById(1L);

        assertNotNull(result);
        assertEquals("Test Customer", result.getName());
    }

    @Test
    void getById_nonExistentCustomer_throwsException() {
        when(customerRepository.findByIdAndTenantId(999L, 1L)).thenReturn(Optional.empty());

        assertThrows(ResourceNotFoundException.class, () -> {
            customerService.getById(999L);
        });
    }

    @Test
    void create_withValidData_createsCustomer() {
        Customer customer = Customer.builder()
                .name("New Customer")
                .phone("1234567890")
                .build();

        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> {
            Customer c = i.getArgument(0);
            c.setId(1L);
            return c;
        });

        Customer result = customerService.create(customer);

        assertNotNull(result);
        assertEquals(1L, result.getTenantId());
        verify(customerRepository).save(any(Customer.class));
    }

    @Test
    void update_withValidData_updatesCustomer() {
        Customer existing = Customer.builder()
                .id(1L)
                .name("Old Name")
                .phone("0000000000")
                .tenantId(1L)
                .build();

        Customer updates = Customer.builder()
                .name("New Name")
                .phone("1234567890")
                .build();

        when(customerRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(existing));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        Customer result = customerService.update(1L, updates);

        assertEquals("New Name", result.getName());
        assertEquals("1234567890", result.getPhone());
    }

    @Test
    void delete_existingCustomer_deletesSuccessfully() {
        Customer customer = Customer.builder()
                .id(1L)
                .tenantId(1L)
                .build();

        when(customerRepository.findByIdAndTenantId(1L, 1L)).thenReturn(Optional.of(customer));
        when(customerRepository.save(any(Customer.class))).thenAnswer(i -> i.getArgument(0));

        customerService.delete(1L);

        assertTrue(customer.getIsDeleted());
        verify(customerRepository).save(customer);
    }
}
