package com.ims.shared.utils;

import java.util.Random;
import org.springframework.stereotype.Component;

@Component
public class CompanyCodeGenerator {

    private final Random random = new Random();

    public String generateCode(String businessName) {
        String prefix = businessName.replaceAll("[^A-Za-z]", "")
                                    .toUpperCase();

        if (prefix.length() >= 4) {
            prefix = prefix.substring(0, 4);
        } else {
            prefix = String.format("%-4s", prefix).replace(' ', 'X');
        }

        int number = 1000 + random.nextInt(9000);

        return prefix + number;
    }
}
