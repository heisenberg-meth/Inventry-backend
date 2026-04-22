package com.ims.tenant.domain.supermarket;

import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

@Service
@Slf4j
public class BarcodeService {
  // Phase 2 — Supermarket barcode fast billing
  // Placeholder for v1.1
  public String lookupBarcode(String barcode, Long tenantId) {
    log.info("Barcode lookup: {}", barcode);
    return barcode;
  }
}
