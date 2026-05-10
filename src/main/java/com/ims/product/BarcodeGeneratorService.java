package com.ims.product;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

@Service
public class BarcodeGeneratorService {

  private static final java.util.Random RANDOM = new java.util.Random();
  private static final int BARCODE_WIDTH = 200;
  private static final int BARCODE_HEIGHT = 80;
  private static final int MARGIN = 10;
  private static final int BAR_GAP = 4;
  private static final int BAR_WIDTH = 2;
  private static final int BASE_BAR_HEIGHT = 40;
  private static final int RANDOM_BAR_HEIGHT_MAX = 20;
  private static final int TEXT_X_OFFSET = 60;
  private static final int TEXT_Y_OFFSET = 75;

  public byte[] generateBarcodeImage(String barcodeText) {
    // Simplified placeholder: Generate an image with the text
    int width = BARCODE_WIDTH;
    int height = BARCODE_HEIGHT;

    BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
    Graphics2D g = image.createGraphics();

    g.setColor(Color.WHITE);
    g.fillRect(0, 0, width, height);

    g.setColor(Color.BLACK);
    // Draw simple "bars"
    for (int i = MARGIN; i < width - MARGIN; i += BAR_GAP) {
      int h = BASE_BAR_HEIGHT + RANDOM.nextInt(RANDOM_BAR_HEIGHT_MAX);
      g.fillRect(i, MARGIN, BAR_WIDTH, h);
    }

    g.drawString(barcodeText != null ? barcodeText : "NO BARCODE", TEXT_X_OFFSET, TEXT_Y_OFFSET);
    g.dispose();

    try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
      ImageIO.write(image, "png", baos);
      return baos.toByteArray();
    } catch (java.io.IOException e) {
      throw new RuntimeException("Failed to generate barcode image", e);
    }
  }
}
