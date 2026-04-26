package com.ims.shared.security;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.util.Base64;
import javax.crypto.Cipher;
import javax.crypto.spec.IvParameterSpec;
import javax.crypto.spec.SecretKeySpec;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;

@Service
public class SecretEncryptionService {

  private static final String ALGORITHM = "AES/CBC/PKCS5Padding";
  private static final int IV_SIZE = 16;
  private static final int KEY_SIZE = 32; // 256-bit

  private final SecretKeySpec keySpec;

  public SecretEncryptionService(@Value("${app.security.encryption-key}") String encryptionKey) {
    byte[] keyBytes = encryptionKey.getBytes(StandardCharsets.UTF_8);
    if (keyBytes.length != KEY_SIZE) {
      throw new IllegalArgumentException("Encryption key must be exactly 32 bytes (256-bit)");
    }
    this.keySpec = new SecretKeySpec(keyBytes, "AES");
  }

  public String encrypt(String plaintext) {
    if (plaintext == null) return null;
    try {
      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.ENCRYPT_MODE, keySpec);
      byte[] iv = cipher.getIV();
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
      byte[] iv = new byte[IV_SIZE];
      byte[] encryptedBytes = new byte[combined.length - IV_SIZE];

      System.arraycopy(combined, 0, iv, 0, IV_SIZE);
      System.arraycopy(combined, IV_SIZE, encryptedBytes, 0, encryptedBytes.length);

      Cipher cipher = Cipher.getInstance(ALGORITHM);
      cipher.init(Cipher.DECRYPT_MODE, keySpec, new IvParameterSpec(iv));
      byte[] decrypted = cipher.doFinal(encryptedBytes);

      return new String(decrypted, StandardCharsets.UTF_8);
    } catch (GeneralSecurityException e) {
      throw new RuntimeException("Decryption failed", e);
    }
  }
}
