package com.ims.shared.utils;

import java.security.SecureRandom;
import org.springframework.stereotype.Component;

@Component
public class CompanyCodeGenerator {

  private static final int PREFIX_LENGTH = 4;
  private static final int RANDOM_SUFFIX_MIN = 1000;
  private static final int RANDOM_SUFFIX_RANGE = 9000;

  private final SecureRandom random = new SecureRandom();

  public String generateCode(String businessName) {
    String prefix = businessName.replaceAll("[^A-Za-z]", "").toUpperCase();

    if (prefix.length() >= PREFIX_LENGTH) {
      prefix = prefix.substring(0, PREFIX_LENGTH);
    } else {
      prefix = String.format("%-" + PREFIX_LENGTH + "s", prefix).replace(' ', 'X');
    }

    int number = RANDOM_SUFFIX_MIN + random.nextInt(RANDOM_SUFFIX_RANGE);

    return prefix + number;
  }
}
