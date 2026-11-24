package com.burdew.app.core;

import java.security.SecureRandom;
import java.util.UUID;

/** Globally unique IDs so records created offline on different phones never collide when synced. */
public final class Ids {
    private static final char[] CODE_ALPHABET = "23456789ABCDEFGHJKLMNPQRSTUVWXYZ".toCharArray();
    private static final SecureRandom RANDOM = new SecureRandom();

    private Ids() {}

    public static String uuid() {
        return UUID.randomUUID().toString();
    }

    /** Short human-friendly reference for SMS and phone calls, e.g. "R-7KQ4M2". No 0/O/1/I. */
    public static String shortCode(String prefix) {
        StringBuilder sb = new StringBuilder(prefix).append('-');
        for (int i = 0; i < 6; i++) sb.append(CODE_ALPHABET[RANDOM.nextInt(CODE_ALPHABET.length)]);
        return sb.toString();
    }
}
