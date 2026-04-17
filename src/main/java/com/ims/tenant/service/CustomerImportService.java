package com.ims.tenant.service;

import com.ims.model.Customer;
import com.ims.tenant.repository.CustomerRepository;
import com.ims.shared.auth.TenantContext;
import java.io.BufferedReader;
import java.io.InputStreamReader;
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
public class CustomerImportService {

    private final CustomerRepository customerRepository;

    @Transactional
    public Map<String, Object> importCustomers(MultipartFile file) {
        List<Customer> customers = new ArrayList<>();
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
                if (data.length < 1) {
                    errors.add("Line " + lineNum + ": Name is required");
                    failCount++;
                    continue;
                }

                try {
                    String name = data[0].trim();
                    String phone = data.length > 1 ? data[1].trim() : null;
                    String email = data.length > 2 ? data[2].trim() : null;
                    String address = data.length > 3 ? data[3].trim() : null;
                    String gstin = data.length > 4 ? data[4].trim() : null;

                    Customer customer = Customer.builder()
                            .tenantId(TenantContext.getTenantId())
                            .name(name)
                            .phone(phone)
                            .email(email)
                            .address(address)
                            .gstin(gstin)
                            .build();

                    customers.add(customer);
                    successCount++;
                } catch (Exception e) {
                    errors.add("Line " + lineNum + ": " + e.getMessage());
                    failCount++;
                }
            }

            customerRepository.saveAll(customers);

        } catch (Exception e) {
            log.error("Failed to import customers", e);
            throw new RuntimeException("Import failed: " + e.getMessage());
        }

        return Map.of(
                "success_count", successCount,
                "fail_count", failCount,
                "errors", errors);
    }
}
