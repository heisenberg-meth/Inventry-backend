package com.ims.tenant.service;

import com.ims.model.Supplier;
import com.ims.shared.auth.TenantContext;
import com.ims.shared.service.BaseImportService;
import com.ims.tenant.repository.SupplierRepository;
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
public class SupplierImportService extends BaseImportService {

  private final SupplierRepository supplierRepository;

  @Transactional
  public Map<String, Object> importSuppliers(MultipartFile file) {
    List<Supplier> suppliers = new ArrayList<>();

    Map<String, Object> result = executeImport(
        file,
        rowData -> {
          if (rowData.length < 1) {
            throw new IllegalArgumentException("Supplier name is required");
          }

          String sName = rowData[0].trim();
          String sPhone = rowData.length > 1 ? rowData[1].trim() : null;
          String sEmail = rowData.length > 2 ? rowData[2].trim() : null;
          String sAddress = rowData.length > 3 ? rowData[3].trim() : null;
          String sGstin = rowData.length > 4 ? rowData[4].trim() : null;

          Supplier supplier = Supplier.builder()
              .tenantId(TenantContext.getTenantId())
              .name(sName)
              .phone(sPhone)
              .email(sEmail)
              .address(sAddress)
              .gstin(sGstin)
              .build();

          suppliers.add(supplier);
        });

    supplierRepository.saveAll(suppliers);

    return result;
  }
}
