package com.example.backend.utils;

/**
 * Normalizes phone numbers to a canonical form (digits only) so that logically
 * duplicate values such as "0912345678", "0912-345-678" and "+84 912 345 678"
 * are treated as the same number for duplicate checks, persistence, and login.
 */
public final class PhoneNumberNormalizer {

    private PhoneNumberNormalizer() {
    }

    public static String normalize(String phoneNumber) {
        if (phoneNumber == null) {
            return null;
        }
        return phoneNumber.replaceAll("\\D", "");
    }
}