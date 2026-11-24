package com.burdew.app.sms;

import android.app.Activity;
import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;

import com.burdew.app.BurdewApp;

/** Records whether an outgoing SMS was sent and (if the network reports it) delivered. */
public class SmsStatusReceiver extends BroadcastReceiver {

    public static final String ACTION_SENT = "com.burdew.app.SMS_SENT";
    public static final String ACTION_DELIVERED = "com.burdew.app.SMS_DELIVERED";
    public static final String EXTRA_ID = "messageId";

    @Override
    public void onReceive(Context context, Intent intent) {
        String id = intent.getStringExtra(EXTRA_ID);
        if (id == null) return;
        boolean ok = getResultCode() == Activity.RESULT_OK;
        String status;
        if (ACTION_DELIVERED.equals(intent.getAction())) status = ok ? "DELIVERED" : "DELIVERY_FAILED";
        else status = ok ? "SENT" : "SEND_FAILED";
        BurdewApp.of(context).repo().updateOutgoingStatus(id, status, ok ? null : "Result code " + getResultCode());
    }
}
