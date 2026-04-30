package com.ims.shared.util;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.util.HexFormat;
import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

public class CryptoUtils {

  private CryptoUtils() {
  }

  public static String hmacSha256(String data, String secret) {
    try {
      Mac mac = Mac.getInstance("HmacSHA256");
      SecretKeySpec keySpec = new SecretKeySpec(secret.getBytes(StandardCharsets.UTF_8), "HmacSHA256");
      mac.init(keySpec);
      byte[] rawHmac = mac.doFinal(data.getBytes(StandardCharsets.UTF_8));
      return bytesToHex(rawHmac);
    } catch (Exception e) {
      throw new RuntimeException("HMAC computation failed", e);
    }
  }

  public static String bytesToHex(byte[] bytes) {
    return HexFormat.of().formatHex(bytes);
  }

  public static boolean constantTimeEquals(String a, String b) {
    if (a == null || b == null)
      return false;
    return MessageDigest.isEqual(
        a.getBytes(StandardCharsets.UTF_8), b.getBytes(StandardCharsets.UTF_8));
  }
}
