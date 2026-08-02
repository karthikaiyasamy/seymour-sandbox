package com.terryfox.hospital.util;

import java.util.regex.Pattern;

/**
 * British Columbia Personal Health Number (PHN) Checksum & PII Masking Utility.
 * Implements official BC Modulus-11 check digit verification algorithm.
 */
public class PhnValidator {

    private static final Pattern PHN_PATTERN = Pattern.compile("^9\\d{9}$");
    private static final int[] WEIGHTS = {2, 4, 8, 5, 10, 9, 7, 3};

    public static boolean isValidPhn(String phn) {
        if (phn == null || !PHN_PATTERN.matcher(phn).matches()) {
            return false;
        }

        int sum = 0;
        for (int i = 1; i < 9; i++) {
            int digit = Character.getNumericValue(phn.charAt(i));
            sum += digit * WEIGHTS[i - 1];
        }

        int remainder = sum % 11;
        if (remainder == 0 || remainder == 1) {
            return false;
        }

        int checkDigit = 11 - remainder;
        int actualCheckDigit = Character.getNumericValue(phn.charAt(9));
        return checkDigit == actualCheckDigit;
    }

    public static String maskPhn(String phn) {
        if (phn == null || phn.length() != 10) {
            return "**********";
        }
        return phn.substring(0, 3) + "****" + phn.substring(7);
    }
}
