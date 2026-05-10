package com.ims.tenant.service;

import com.ims.model.Customer;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.service.BaseImportService;
import com.ims.tenant.repository.CustomerRepository;
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
public class CustomerImportService extends BaseImportService {

  private final CustomerRepository customerRepository;

  @Transactional
  public Map<String, Object> importCustomers(MultipartFile file) {
    List<Customer> customers = new ArrayList<>();

    Map<String, Object> result =
        executeImport(
            file,
            data -> {
              if (data.length < 1) {
                throw new IllegalArgumentException("Name is required");
              }

              String name = data[0].trim();
              String phone = data.length > 1 ? data[1].trim() : null;
              String email = data.length > 2 ? data[2].trim() : null;
              String address = data.length > 3 ? data[3].trim() : null;
              String gstin = data.length > 4 ? data[4].trim() : null;

              Customer customer =
                  Customer.builder()
                      .tenantId(TenantContext.getTenantId())
                      .name(name)
                      .phone(phone)
                      .email(email)
                      .address(address)
                      .gstin(gstin)
                      .build();

              customers.add(customer);
            });

    customerRepository.saveAll(customers);

    return result;
  }
}
