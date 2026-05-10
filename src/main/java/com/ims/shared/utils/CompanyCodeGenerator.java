package com.ims.shared.utils;

import java.security.SecureRandom;
import java.util.Locale;
import org.springframework.stereotype.Component;

@Component
public class CompanyCodeGenerator {

  private static final int CODE_MIN_VALUE = 1000;
  private static final int CODE_RANDOM_RANGE = 9000;
  private static final int PREFIX_LENGTH = 4;
  private final SecureRandom random = new SecureRandom();

  public String generateCode(String businessName) {
    String prefix = businessName.replaceAll("[^A-Za-z]", "").toUpperCase(Locale.ROOT);

    if (prefix.length() >= PREFIX_LENGTH) {
      prefix = prefix.substring(0, PREFIX_LENGTH);
    } else {
      prefix = String.format("%-4s", prefix).replace(' ', 'X');
    }

    int number = CODE_MIN_VALUE + random.nextInt(CODE_RANDOM_RANGE);

    return prefix + number;
  }
}
