package com.ims.product;

import java.awt.Color;
import java.awt.Graphics2D;
import java.awt.image.BufferedImage;
import java.io.ByteArrayOutputStream;
import javax.imageio.ImageIO;
import org.springframework.stereotype.Service;

@Service
public class BarcodeGeneratorService {

    public byte[] generateBarcodeImage(String barcodeText) {
        // Simplified placeholder: Generate an image with the text
        int width = 200;
        int height = 80;
        
        BufferedImage image = new BufferedImage(width, height, BufferedImage.TYPE_INT_RGB);
        Graphics2D g = image.createGraphics();
        
        g.setColor(Color.WHITE);
        g.fillRect(0, 0, width, height);
        
        g.setColor(Color.BLACK);
        // Draw simple "bars"
        for (int i = 10; i < width - 10; i += 4) {
            int h = 40 + (int)(Math.random() * 20);
            g.fillRect(i, 10, 2, h);
        }
        
        g.drawString(barcodeText != null ? barcodeText : "NO BARCODE", 60, 75);
        g.dispose();
        
        try (ByteArrayOutputStream baos = new ByteArrayOutputStream()) {
            ImageIO.write(image, "png", baos);
            return baos.toByteArray();
        } catch (Exception e) {
            throw new RuntimeException("Failed to generate barcode image", e);
        }
    }
}
