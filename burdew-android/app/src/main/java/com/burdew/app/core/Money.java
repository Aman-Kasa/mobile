package com.burdew.app.core;

import java.math.BigDecimal;
import java.math.RoundingMode;

/**
 * Amounts are stored as long minor units (cents) to avoid floating-point errors.
 * 50 NFA == 5000.
 */
public final class Money {
    private Money() {}

    public static long fromNfa(long nfa) {
        return Math.multiplyExact(nfa, 100L);
    }

    /** Parses user input like "250", "250.5", "1,200.75". Returns null if invalid or negative. */
    public static Long parse(String input) {
        if (input == null) return null;
        String s = input.trim().replace(",", "");
        if (s.isEmpty()) return null;
        try {
            BigDecimal d = new BigDecimal(s);
            if (d.signum() < 0 || d.scale() > 2) return null;
            return d.movePointRight(2).longValueExact();
        } catch (NumberFormatException | ArithmeticException e) {
            return null;
        }
    }

    public static String format(long minor) {
        BigDecimal d = BigDecimal.valueOf(minor).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY);
        return String.format(java.util.Locale.ROOT, "%,.2f NFA", d);
    }

    /** Plain decimal for edit fields and CSV ("1200.50"). */
    public static String plain(long minor) {
        return BigDecimal.valueOf(minor).movePointLeft(2).setScale(2, RoundingMode.UNNECESSARY).toPlainString();
    }
}
