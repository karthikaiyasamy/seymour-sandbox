package com.healthcare.sandbox.util;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.junit.jupiter.api.Assertions.*;

class PhnValidatorTest {

    @Test
    @DisplayName("Should validate valid BC Personal Health Number (PHN) Modulus-11 checksum")
    void testValidPhnChecksum() {
        // Test BC PHN starting with 9 (9000000082)
        // sum = (0*2 + 0*4 + 0*8 + 0*5 + 0*10 + 0*9 + 0*7 + 8*3) = 24 % 11 = 2 -> 11 - 2 = 9
        // 9000000099: sum = 9*3 = 27 % 11 = 5 -> 11-5 = 6
        // Let's test 9000000027: sum = 2*3 = 6 % 11 = 6 -> 11-6 = 5
        // Let's test 9000000074: sum = 7*3 = 21 % 11 = 10 -> check digit 1 -> 9000000071
        assertTrue(PhnValidator.isValidBCOnlyPHN("9000000071"));
    }

    @Test
    @DisplayName("Should reject invalid BC PHN checksum")
    void testInvalidPhnChecksum() {
        assertFalse(PhnValidator.isValidBCOnlyPHN("9000000078")); // Invalid check digit
        assertFalse(PhnValidator.isValidBCOnlyPHN("1234567890"));
    }

    @Test
    @DisplayName("Should reject malformed PHN strings")
    void testMalformedPhnInput() {
        assertFalse(PhnValidator.isValidBCOnlyPHN(null));
        assertFalse(PhnValidator.isValidBCOnlyPHN(""));
        assertFalse(PhnValidator.isValidBCOnlyPHN("92345")); // Too short
        assertFalse(PhnValidator.isValidBCOnlyPHN("ABCDEFGHIJ")); // Non-numeric
    }

    @Test
    @DisplayName("Should correctly mask PHN for PII privacy logging compliance")
    void testPhnMasking() {
        assertEquals("923****897", PhnValidator.maskPHN("9234567897"));
    }
}
