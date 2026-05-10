package com.ims.shared.service;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.function.Consumer;
import lombok.extern.slf4j.Slf4j;
import org.springframework.web.multipart.MultipartFile;

@Slf4j
public abstract class BaseImportService {

  protected Map<String, Object> executeImport(MultipartFile file, Consumer<String[]> rowProcessor) {
    int successCount = 0;
    int failCount = 0;
    List<String> errors = new ArrayList<>();

    try (BufferedReader reader =
        new BufferedReader(new InputStreamReader(file.getInputStream(), StandardCharsets.UTF_8))) {
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
        try {
          rowProcessor.accept(data);
          successCount++;
        } catch (Exception e) {
          errors.add("Line " + lineNum + ": " + e.getMessage());
          failCount++;
        }
      }
    } catch (java.io.IOException | RuntimeException e) {
      log.error("Failed to process import file", e);
      throw new RuntimeException("Import failed: " + e.getMessage());
    }

    return Map.of(
        "success_count", successCount,
        "fail_count", failCount,
        "errors", errors);
  }
}
