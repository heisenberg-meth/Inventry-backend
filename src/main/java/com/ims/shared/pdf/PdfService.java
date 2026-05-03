package com.ims.shared.pdf;

import java.io.ByteArrayOutputStream;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.thymeleaf.TemplateEngine;
import org.thymeleaf.context.Context;
import org.xhtmlrenderer.pdf.ITextRenderer;

@Service
@RequiredArgsConstructor
@Slf4j
public class PdfService {

  private final TemplateEngine templateEngine;

  public byte[] generatePdfFromHtml(String templateName, Context context) {
    String html = templateEngine.process(templateName, context);
    try (ByteArrayOutputStream outputStream = new ByteArrayOutputStream()) {
      ITextRenderer renderer = new ITextRenderer();
      renderer.setDocumentFromString(html);
      renderer.layout();
      renderer.createPDF(outputStream);
      return outputStream.toByteArray();
    } catch (Exception e) {
      log.error("Error generating PDF from template {}: {}", templateName, e.getMessage(), e);
      throw new RuntimeException("Failed to generate PDF: " + e.getMessage(), e);
    }
  }
}
