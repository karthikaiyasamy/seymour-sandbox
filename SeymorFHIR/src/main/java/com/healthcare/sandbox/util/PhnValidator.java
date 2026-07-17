package com.healthcare.sandbox.util;

import java.util.regex.Pattern;

public class PhnValidator {
    // BC PHNs are strictly 10 digits and start with a 9
    private static final Pattern PHN_PATTERN = Pattern.compile("^9\\d{9}$");

    /**
     * Validates a BC PHN using the standard Modulus-11 check digit algorithm.
     */
    public static boolean isValidBCOnlyPHN(String phn) {
        if (phn == null || !PHN_PATTERN.matcher(phn).matches()) {
            return false;
        }

        // Modulus 11 weights for BC Health Card validation (Digits 2 through 9)
        int[] weights = { 2, 4, 8, 5, 10, 9, 7, 3 };
        int sum = 0;

        // Loop index 1 through 8 (representing digits 2 through 9)
        for (int i = 1; i < 9; i++) {
            int digit = Character.getNumericValue(phn.charAt(i));
            sum += digit * weights[i - 1]; // weights index is 0-7
        }

        int remainder = sum % 11;
        
        // If remainder is 0 or 1, the check digit would be 11 or 10, which is invalid
        if (remainder == 0 || remainder == 1) {
            return false;
        }

        int checkDigit = 11 - remainder;
        int providedCheckDigit = Character.getNumericValue(phn.charAt(9));

        return checkDigit == providedCheckDigit;
    }

    /**
     * Masks the sensitive middle digits of a PHN for unsafe outputs/logs.
     * Replaces characters index 3 through 6 with asterisks.
     * e.g., "9123456789" -> "912****789"
     */
    public static String maskPHN(String phn) {
        if (phn == null || phn.length() != 10) {
            return "**********"; // Fallback safe mask
        }
        return phn.substring(0, 3) + "****" + phn.substring(7);
    }
}
