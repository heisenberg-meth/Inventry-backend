package com.ims.shared.utils;

import java.util.Random;

public class CompanyCodeGenerator {

    private static final Random RANDOM = new Random();

    public static String generateCode(String businessName) {
        String prefix = businessName.replaceAll("[^A-Za-z]", "")
                                    .toUpperCase();

        if (prefix.length() >= 4) {
            prefix = prefix.substring(0, 4);
        } else {
            prefix = String.format("%-4s", prefix).replace(' ', 'X');
        }

        int number = 1000 + RANDOM.nextInt(9000);

        return prefix + number;
    }
}
