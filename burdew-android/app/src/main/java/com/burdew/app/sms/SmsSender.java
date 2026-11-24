package com.burdew.app.sms;

import android.Manifest;
import android.app.PendingIntent;
import android.content.Context;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Build;
import android.telephony.SmsManager;

import androidx.core.content.ContextCompat;

import com.burdew.app.data.BurdewRepository;

import java.util.ArrayList;

/**
 * Sends SMS and records them as OUT messages. Sent/delivered results come back to
 * SmsStatusReceiver, which updates the record. Delivery is never assumed.
 * Safe to call from a background thread.
 */
public final class SmsSender {
    private SmsSender() {}

    public static boolean canSend(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.SEND_SMS) == PackageManager.PERMISSION_GRANTED
                && c.getPackageManager().hasSystemFeature(PackageManager.FEATURE_TELEPHONY);
    }

    /** Returns the stored message id. If sending is not possible the message is kept as SEND_FAILED for manual follow-up. */
    public static String send(Context context, BurdewRepository repo, String phone, String body) {
        String id = repo.recordOutgoingSmsSync(phone, body);
        if (!canSend(context)) {
            repo.updateOutgoingStatus(id, "SEND_FAILED", "No SMS permission or no SIM");
            return id;
        }
        try {
            SmsManager sms = SmsManager.getDefault();
            ArrayList<String> parts = sms.divideMessage(body);
            ArrayList<PendingIntent> sent = new ArrayList<>();
            ArrayList<PendingIntent> delivered = new ArrayList<>();
            int flags = PendingIntent.FLAG_UPDATE_CURRENT | (Build.VERSION.SDK_INT >= 23 ? PendingIntent.FLAG_IMMUTABLE : 0);
            for (int i = 0; i < parts.size(); i++) {
                // Only the last part reports, so a multipart message gets one status.
                boolean last = i == parts.size() - 1;
                sent.add(last ? PendingIntent.getBroadcast(context, id.hashCode(),
                        status(context, id, SmsStatusReceiver.ACTION_SENT), flags) : null);
                delivered.add(last ? PendingIntent.getBroadcast(context, id.hashCode() + 1,
                        status(context, id, SmsStatusReceiver.ACTION_DELIVERED), flags) : null);
            }
            sms.sendMultipartTextMessage(phone, null, parts, sent, delivered);
        } catch (RuntimeException e) {
            repo.updateOutgoingStatus(id, "SEND_FAILED", e.getMessage());
        }
        return id;
    }

    private static Intent status(Context c, String id, String action) {
        return new Intent(c, SmsStatusReceiver.class).setAction(action).putExtra(SmsStatusReceiver.EXTRA_ID, id);
    }
}
