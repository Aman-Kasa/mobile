package com.burdew.app.core;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Locale;
import java.util.Map;

/**
 * Parses structured customer SMS requests such as:
 *
 *   SERVICE: ELECTRICITY
 *   NAME: Customer Name
 *   PHONE: 07123456
 *   ADDRESS: Mai Temenai, near the school
 *   ACCOUNT: 123456
 *
 * Tolerant of how real phones deliver text: fields may be split by new lines or ';',
 * keys are case-insensitive, '=' works as well as ':', and PHONE defaults to the sender.
 * Pure Java so it can be unit-tested off-device.
 */
public final class SmsRequestParser {

    public static final class Result {
        public final boolean valid;
        public final ServiceType service;
        public final String name;
        public final String phone;
        public final String address;
        public final String account;
        public final String note;
        public final List<String> errors;

        Result(ServiceType service, String name, String phone, String address, String account,
               String note, List<String> errors) {
            this.service = service;
            this.name = name;
            this.phone = phone;
            this.address = address;
            this.account = account;
            this.note = note;
            this.errors = Collections.unmodifiableList(errors);
            this.valid = errors.isEmpty();
        }
    }

    private static final Map<String, String> KEY_ALIASES = new LinkedHashMap<>();
    static {
        alias("SERVICE", "SERVICE", "TYPE", "SRV");
        alias("NAME", "NAME", "FULLNAME", "CUSTOMER");
        alias("PHONE", "PHONE", "TEL", "MOBILE");
        alias("ADDRESS", "ADDRESS", "ADDR", "LOCATION");
        alias("ACCOUNT", "ACCOUNT", "ACC", "ACCT", "METER", "REF");
        alias("NOTE", "NOTE", "NOTES", "INFO");
    }

    private static void alias(String canonical, String... names) {
        for (String n : names) KEY_ALIASES.put(n, canonical);
    }

    private SmsRequestParser() {}

    /** Quick check used by the SMS receiver to ignore ordinary personal messages. */
    public static boolean looksLikeRequest(String body) {
        if (body == null) return false;
        String u = body.toUpperCase(Locale.ROOT);
        return u.contains("SERVICE") && (u.contains("ACCOUNT") || u.contains("ACC"));
    }

    public static Result parse(String body, String senderPhone) {
        Map<String, String> fields = new LinkedHashMap<>();
        List<String> errors = new ArrayList<>();
        if (body == null || body.trim().isEmpty()) {
            errors.add("Empty message");
            return new Result(null, null, null, null, null, null, errors);
        }
        for (String rawLine : body.split("[\\r\\n;]+")) {
            String line = rawLine.trim();
            if (line.isEmpty()) continue;
            int sep = indexOfSeparator(line);
            if (sep <= 0) continue;
            String key = line.substring(0, sep).trim().toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
            String value = line.substring(sep + 1).trim();
            String canonical = KEY_ALIASES.get(key);
            if (canonical != null && !value.isEmpty() && !fields.containsKey(canonical)) {
                fields.put(canonical, value);
            }
        }

        ServiceType service = ServiceType.parse(fields.get("SERVICE"));
        if (fields.get("SERVICE") == null) errors.add("Missing SERVICE");
        else if (service == null) errors.add("Unknown SERVICE (use ELECTRICITY or WATER)");

        String name = fields.get("NAME");
        if (name == null) errors.add("Missing NAME");

        String account = fields.get("ACCOUNT");
        if (account == null) errors.add("Missing ACCOUNT");
        else if (account.length() > 40) errors.add("ACCOUNT is too long");

        String phone = PhoneNumbers.normalize(fields.containsKey("PHONE") ? fields.get("PHONE") : senderPhone);
        if (phone == null || !PhoneNumbers.isPlausible(phone)) errors.add("Missing or invalid PHONE");

        return new Result(service, name, phone, fields.get("ADDRESS"), account, fields.get("NOTE"), errors);
    }

    private static int indexOfSeparator(String line) {
        int colon = line.indexOf(':');
        int eq = line.indexOf('=');
        if (colon < 0) return eq;
        if (eq < 0) return colon;
        return Math.min(colon, eq);
    }

    /**
     * Stable fingerprint for duplicate detection: same sender + same content (ignoring
     * case and whitespace) yields the same value, so re-delivered SMS are caught.
     */
    public static String fingerprint(String senderPhone, String body) {
        String normSender = PhoneNumbers.normalize(senderPhone);
        String normBody = body == null ? "" : body.toUpperCase(Locale.ROOT).replaceAll("\\s+", "");
        try {
            MessageDigest md = MessageDigest.getInstance("SHA-256");
            byte[] d = md.digest((normSender + "|" + normBody).getBytes(StandardCharsets.UTF_8));
            StringBuilder sb = new StringBuilder();
            for (int i = 0; i < 16; i++) sb.append(String.format("%02x", d[i]));
            return sb.toString();
        } catch (NoSuchAlgorithmException e) {
            throw new IllegalStateException(e);
        }
    }

    /** Short reply that fits in a single 160-character GSM-7 SMS. */
    public static String acknowledgement(String requestCode, ServiceType service) {
        String svc = service == ServiceType.WATER ? "water" : "electricity";
        return "BURDEW: Your " + svc + " bill request was received. Ref " + requestCode
                + ". A provider will contact you soon.";
    }

    public static String rejection(List<String> errors) {
        String first = errors.isEmpty() ? "Invalid format" : errors.get(0);
        String msg = "BURDEW: Request not accepted (" + first + "). Format: SERVICE: ELECTRICITY; NAME: ...; ADDRESS: ...; ACCOUNT: ...";
        return msg.length() > 160 ? msg.substring(0, 160) : msg;
    }
}
