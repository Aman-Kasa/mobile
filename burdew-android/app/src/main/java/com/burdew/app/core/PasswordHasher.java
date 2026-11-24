package com.burdew.app.core;

import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.MessageDigest;
import java.security.SecureRandom;

import javax.crypto.Mac;
import javax.crypto.spec.SecretKeySpec;

/**
 * PBKDF2-HMAC-SHA256 implemented on top of javax.crypto.Mac, because the built-in
 * "PBKDF2WithHmacSHA256" SecretKeyFactory only exists from Android 8.0 (API 26)
 * and BURDEW targets older phones (minSdk 21). HmacSHA256 is available on every API level.
 *
 * Stored format: pbkdf2_sha256$iterations$saltHex$hashHex
 * (hex, not java.util.Base64, which only exists from Android API 26)
 */
public final class PasswordHasher {
    public static final int DEFAULT_ITERATIONS = 120_000;
    private static final int SALT_BYTES = 16;
    private static final int KEY_BYTES = 32;
    private static final String PREFIX = "pbkdf2_sha256";
    private static final SecureRandom RANDOM = new SecureRandom();

    private PasswordHasher() {}

    public static String hash(char[] password) {
        return hash(password, DEFAULT_ITERATIONS);
    }

    public static String hash(char[] password, int iterations) {
        byte[] salt = new byte[SALT_BYTES];
        RANDOM.nextBytes(salt);
        byte[] dk = pbkdf2(password, salt, iterations, KEY_BYTES);
        return PREFIX + "$" + iterations + "$" + toHex(salt) + "$" + toHex(dk);
    }

    public static boolean verify(char[] password, String stored) {
        if (password == null || stored == null) return false;
        String[] parts = stored.split("\\$");
        if (parts.length != 4 || !PREFIX.equals(parts[0])) return false;
        try {
            int iterations = Integer.parseInt(parts[1]);
            if (iterations < 1) return false;
            byte[] salt = fromHex(parts[2]);
            byte[] expected = fromHex(parts[3]);
            byte[] actual = pbkdf2(password, salt, iterations, expected.length);
            return MessageDigest.isEqual(expected, actual); // constant-time compare
        } catch (IllegalArgumentException e) {
            return false;
        }
    }

    static String toHex(byte[] b) {
        StringBuilder sb = new StringBuilder(b.length * 2);
        for (byte x : b) sb.append(Character.forDigit((x >> 4) & 0xF, 16)).append(Character.forDigit(x & 0xF, 16));
        return sb.toString();
    }

    static byte[] fromHex(String s) {
        if (s.length() % 2 != 0) throw new IllegalArgumentException("odd hex length");
        byte[] out = new byte[s.length() / 2];
        for (int i = 0; i < out.length; i++) {
            int hi = Character.digit(s.charAt(2 * i), 16), lo = Character.digit(s.charAt(2 * i + 1), 16);
            if (hi < 0 || lo < 0) throw new IllegalArgumentException("bad hex");
            out[i] = (byte) ((hi << 4) | lo);
        }
        return out;
    }

    /** RFC 8018 section 5.2. */
    static byte[] pbkdf2(char[] password, byte[] salt, int iterations, int keyLen) {
        try {
            Mac mac = Mac.getInstance("HmacSHA256");
            byte[] pw = new String(password).getBytes(StandardCharsets.UTF_8);
            // HMAC rejects empty keys; an empty password is refused at the UI level anyway.
            mac.init(new SecretKeySpec(pw.length == 0 ? new byte[1] : pw, "HmacSHA256"));
            int hLen = mac.getMacLength();
            int blocks = (keyLen + hLen - 1) / hLen;
            byte[] out = new byte[keyLen];
            int offset = 0;
            for (int i = 1; i <= blocks; i++) {
                mac.update(salt);
                mac.update(new byte[]{(byte) (i >>> 24), (byte) (i >>> 16), (byte) (i >>> 8), (byte) i});
                byte[] u = mac.doFinal();
                byte[] t = u.clone();
                for (int c = 1; c < iterations; c++) {
                    u = mac.doFinal(u);
                    for (int k = 0; k < t.length; k++) t[k] ^= u[k];
                }
                int len = Math.min(hLen, keyLen - offset);
                System.arraycopy(t, 0, out, offset, len);
                offset += len;
            }
            return out;
        } catch (GeneralSecurityException e) {
            throw new IllegalStateException("HmacSHA256 unavailable", e);
        }
    }
}
