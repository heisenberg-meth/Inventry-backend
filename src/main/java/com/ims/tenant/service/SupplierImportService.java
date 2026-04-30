package com.ims.tenant.service;

import com.ims.model.Supplier;
import com.ims.tenant.repository.SupplierRepository;
import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import java.util.Map;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;

@Service
@RequiredArgsConstructor
@Slf4j
public class SupplierImportService {

  // CSV column indexes: Name, Phone, Email, Address, GSTIN
  private static final int COL_ADDRESS_INDEX = 3;
  private static final int MIN_COLUMNS_FOR_ADDRESS = 3;
  private static final int COL_GSTIN_INDEX = 4;
  private static final int MIN_COLUMNS_FOR_GSTIN = 4;

  private final SupplierRepository supplierRepository;

  @Transactional
  public Map<String, Object> importSuppliers(MultipartFile file, boolean dryRun) {
    List<Supplier> suppliers = new ArrayList<>();
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
          String address =
              data.length > MIN_COLUMNS_FOR_ADDRESS ? data[COL_ADDRESS_INDEX].trim() : null;
          String gstin = data.length > MIN_COLUMNS_FOR_GSTIN ? data[COL_GSTIN_INDEX].trim() : null;

          Supplier supplier =
              Supplier.builder()
                  .name(Objects.requireNonNull(name))
                  .phone(phone)
                  .email(email)
                  .address(address)
                  .gstin(gstin)
                  .build();

          suppliers.add(supplier);
          successCount++;
        } catch (Exception e) {
          errors.add("Line " + lineNum + ": " + e.getMessage());
          failCount++;
        }
      }

      if (dryRun) {
        return Objects.requireNonNull(
            Map.of(
                "success_count",
                successCount,
                "fail_count",
                failCount,
                "errors",
                errors,
                "status",
                "DRY_RUN_SUCCESS"));
      }

      if (!suppliers.isEmpty()) {
        try {
          supplierRepository.saveAll(suppliers);
        } catch (DataIntegrityViolationException e) {
          throw new IllegalStateException("Import failed due to DB constraint", e);
        }
      }

    } catch (Exception e) {
      log.error("Failed to import suppliers", e);
      throw new RuntimeException("Import failed: " + e.getMessage());
    }

    return Objects.requireNonNull(
        Map.of(
            "success_count",
            successCount,
            "fail_count",
            failCount,
            "errors",
            errors,
            "status",
            "SUCCESS"));
  }
}
