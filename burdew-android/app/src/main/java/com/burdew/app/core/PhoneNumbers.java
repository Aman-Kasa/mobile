package com.burdew.app.core;

/** Normalizes Eritrean phone numbers to +291... so the same person is matched however they type it. */
public final class PhoneNumbers {
    public static final String COUNTRY_CODE = "291";

    private PhoneNumbers() {}

    public static String normalize(String raw) {
        if (raw == null) return null;
        String s = raw.replaceAll("[\\s\\-().]", "");
        if (s.isEmpty()) return null;
        if (s.startsWith("+")) return "+" + s.substring(1).replaceAll("\\D", "");
        s = s.replaceAll("\\D", "");
        if (s.isEmpty()) return null;
        if (s.startsWith("00")) return "+" + s.substring(2);
        if (s.startsWith(COUNTRY_CODE) && s.length() > 8) return "+" + s;
        if (s.startsWith("0")) return "+" + COUNTRY_CODE + s.substring(1);
        return "+" + COUNTRY_CODE + s;
    }

    /** Loose sanity check: country code plus 6-9 subscriber digits. */
    public static boolean isPlausible(String normalized) {
        return normalized != null && normalized.matches("\\+\\d{8,14}");
    }
}
