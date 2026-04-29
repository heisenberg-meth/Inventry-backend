package com.ims.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.SecureRandom;
import java.util.Base64;
import java.util.HexFormat;
import javax.crypto.Cipher;
import javax.crypto.spec.GCMParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.stereotype.Service;

@Service
public class SecretEncryptionService {

  private static final String ALGORITHM = "AES/GCM/NoPadding";
  private static final int IV_SIZE = 12; // recommended for GCM
  private static final int TAG_LENGTH = 128;
  private static final int KEY_SIZE = 32; // 256-bit

  private final SecretKeySpec keySpec;

  public SecretEncryptionService(com.ims.config.AppProperties appProperties) {
    String encryptionKey = appProperties.getSecurity().getEncryptionKey();
    if (encryptionKey == null || encryptionKey.isBlank()) {
      throw new IllegalArgumentException("Encryption key must not be blank");
    }

    byte[] keyBytes;
    if (encryptionKey.length() == 64) {
      try {
        keyBytes = HexFormat.of().parseHex(encryptionKey);
      } catch (IllegalArgumentException e) {
        keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
      }
    } else {
      try {
        keyBytes = Base64.getDecoder().decode(encryptionKey);
      } catch (IllegalArgumentException e) {
        keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
      }
    }

    if (keyBytes.length != KEY_SIZE) {
      throw new IllegalArgumentException(
          "Encryption key must be exactly 32 bytes (256-bit) when decoded. Provided size: "
              + keyBytes.length);
    }
    this.keySpec = new SecretKeySpec(keyBytes, "AES");
  }

  public String encrypt(String plaintext) {
    if (plaintext == null) return null;

    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM);

      byte[] iv = new byte[IV_SIZE];
      SecureRandom random = new SecureRandom();
      random.nextBytes(iv);

      GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);
      cipher.init(Cipher.ENCRYPT_MODE, keySpec, spec);

      byte[] encrypted = cipher.doFinal(plaintext.getBytes(StandardCharsets.UTF_8));

      byte[] combined = new byte[iv.length + encrypted.length];
      System.arraycopy(iv, 0, combined, 0, iv.length);
      System.arraycopy(encrypted, 0, combined, iv.length, encrypted.length);

      return Base64.getEncoder().encodeToString(combined);

    } catch (GeneralSecurityException e) {
      throw new RuntimeException("Encryption failed", e);
    }
  }

  public String decrypt(String encrypted) {
    if (encrypted == null) return null;

    try {
      byte[] combined = Base64.getDecoder().decode(encrypted);
      if (combined.length < IV_SIZE) {
        throw new IllegalArgumentException("Invalid encrypted data: too short");
      }

      byte[] iv = new byte[IV_SIZE];
      byte[] encryptedBytes = new byte[combined.length - IV_SIZE];

      System.arraycopy(combined, 0, iv, 0, IV_SIZE);
      System.arraycopy(combined, IV_SIZE, encryptedBytes, 0, encryptedBytes.length);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      GCMParameterSpec spec = new GCMParameterSpec(TAG_LENGTH, iv);

      cipher.init(Cipher.DECRYPT_MODE, keySpec, spec);

      byte[] decrypted = cipher.doFinal(encryptedBytes);

      return new String(decrypted, StandardCharsets.UTF_8);

    } catch (GeneralSecurityException e) {
      throw new RuntimeException("Decryption failed", e);
    }
  }
}

