package com.burdew.app.config;

import com.burdew.app.core.Money;

/**
 * Technical identifiers in one place. User-visible branding (name, tagline) lives in
 * res/values/strings.xml, so a rename only touches resources and this file.
 */
public final class AppConfig {
    private AppConfig() {}

    public static final String DATABASE_NAME = "burdew.db";
    public static final String MAPS_DIR = "maps";            // offline region files: <region>.map
    public static final String RECEIPTS_DIR = "receipts";
    public static final String DEFAULT_REGION = "asmara";

    /** Asmara city centre, used when no GPS fix is available. */
    public static final double DEFAULT_LAT = 15.3229;
    public static final double DEFAULT_LON = 38.9251;

    /** Default compensation per verified transaction. Editable by admins in Settings. */
    public static final long DEFAULT_PROVIDER_FEE = Money.fromNfa(50);

    /** Same sender + same text inside this window is treated as a re-delivered duplicate. */
    public static final long SMS_DUPLICATE_WINDOW_MS = 24L * 60 * 60 * 1000;

    public static final int MAX_FAILED_LOGINS = 5;
    public static final long LOCKOUT_MS = 5L * 60 * 1000;

    /** Receipt photos are downscaled to this long edge and JPEG quality to save storage. */
    public static final int RECEIPT_MAX_EDGE_PX = 1600;
    public static final int RECEIPT_JPEG_QUALITY = 80;

    // Settings keys
    public static final String KEY_PROVIDER_FEE = "provider_fee_minor";
    public static final String KEY_SMS_AUTO_ACK = "sms_auto_ack";
    public static final String KEY_SMS_AUTO_CREATE = "sms_auto_create";
    public static final String KEY_ONBOARDED = "onboarded";
}
