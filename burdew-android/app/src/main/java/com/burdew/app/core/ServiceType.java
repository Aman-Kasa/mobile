package com.burdew.app.core;

import java.util.Locale;

/** Services offered. New household services are added here (Phase 8). */
public enum ServiceType {
    ELECTRICITY,
    WATER;

    /** Lenient parsing for SMS and imports: "electricity", "elec", "E", "power", "water", "W". */
    public static ServiceType parse(String raw) {
        if (raw == null) return null;
        String s = raw.trim().toUpperCase(Locale.ROOT);
        switch (s) {
            case "ELECTRICITY": case "ELECTRIC": case "ELEC": case "E": case "POWER": case "EEC":
                return ELECTRICITY;
            case "WATER": case "W":
                return WATER;
            default:
                return null;
        }
    }
}
