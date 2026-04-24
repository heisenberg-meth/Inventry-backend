package com.ims.product;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

@Service
public class BarcodeGeneratorService {

  // Dimensions and layout constants for the placeholder barcode image.
  private static final int IMAGE_WIDTH = 200;
  private static final int IMAGE_HEIGHT = 80;
  private static final int BAR_START_X = 10;
  private static final int BAR_END_MARGIN = 10;
  private static final int BAR_SPACING = 4;
  private static final int BAR_WIDTH = 2;
  private static final int BAR_Y = 10;
  private static final int BAR_BASE_HEIGHT = 40;
  private static final int BAR_RANDOM_RANGE = 20;
  private static final int TEXT_X = 60;
  private static final int TEXT_Y = 75;

  public byte[] generateBarcodeImage(String barcodeText) {
    // Simplified placeholder: generate an image with the text
    BufferedImage image = new BufferedImage(IMAGE_WIDTH, IMAGE_HEIGHT, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();

    g.setColor(Color.WHITE);
    g.fillRect(0, 0, IMAGE_WIDTH, IMAGE_HEIGHT);

    g.setColor(Color.BLACK);
    // Draw simple "bars"
    for (int i = BAR_START_X; i < IMAGE_WIDTH - BAR_END_MARGIN; i += BAR_SPACING) {
      int h = BAR_BASE_HEIGHT + (int) (Math.random() * BAR_RANDOM_RANGE);
      g.fillRect(i, BAR_Y, BAR_WIDTH, h);
    }

    g.drawString(barcodeText != null ? barcodeText : "NO BARCODE", TEXT_X, TEXT_Y);
    g.dispose();

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", baos);
      return baos.toByteArray();
    } catch (Exception e) {
      throw new RuntimeException("Failed to generate barcode image", e);
    }
  }
}
