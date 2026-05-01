package com.ims.tenant.service;

import com.ims.model.Customer;
import com.ims.shared.auth.TenantContext;
import com.ims.tenant.repository.CustomerRepository;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class CustomerImportService {

  // CSV column indexes: Name, Phone, Email, Address, GSTIN
  private static final int COL_ADDRESS_INDEX = 3;
  private static final int MIN_COLUMNS_FOR_ADDRESS = 3;
  private static final int COL_GSTIN_INDEX = 4;
  private static final int MIN_COLUMNS_FOR_GSTIN = 4;

  private final CustomerRepository customerRepository;

  @Transactional
  public Map<String, Object> importCustomers(MultipartFile file, boolean dryRun) {
    List<Customer> customers = new ArrayList<>();
    int successCount = 0;
    int failCount = 0;
    List<String> errors = new ArrayList<>();
    Long tenantId = TenantContext.requireTenantId();

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
          String address = data.length > MIN_COLUMNS_FOR_ADDRESS ? data[COL_ADDRESS_INDEX].trim() : null;
          String gstin = data.length > MIN_COLUMNS_FOR_GSTIN ? data[COL_GSTIN_INDEX].trim() : null;

          Customer customer = Objects.requireNonNull(
              Customer.builder()
                  .tenantId(tenantId)
                  .name(Objects.requireNonNull(name))
                  .phone(phone)
                  .email(email)
                  .address(address)
                  .gstin(gstin)
                  .build());

          customers.add(customer);
          successCount++;
        } catch (Exception e) {
          errors.add("Line " + lineNum + ": " + e.getMessage());
          failCount++;
        }
      }

      if (!errors.isEmpty()) {
        return Objects.requireNonNull(
            Map.of(
                "success_count", 0, "fail_count", failCount, "errors", errors, "status", "FAILED"));
      }

      if (dryRun) {
        return Objects.requireNonNull(
            Map.of(
                "success_count",
                successCount,
                "fail_count",
                0,
                "errors",
                new ArrayList<>(),
                "status",
                "DRY_RUN_SUCCESS"));
      }

      if (!customers.isEmpty()) {
        try {
          customerRepository.saveAll(customers);
        } catch (DataIntegrityViolationException e) {
          throw new IllegalStateException("Import failed due to DB constraint", e);
        }
      }

    } catch (Exception e) {
      log.error("Failed to import customers", e);
      throw new RuntimeException("Import failed: " + e.getMessage());
    }

    return Objects.requireNonNull(
        Map.of(
            "success_count", successCount, "fail_count", 0, "errors", errors, "status", "SUCCESS"));
  }
}
