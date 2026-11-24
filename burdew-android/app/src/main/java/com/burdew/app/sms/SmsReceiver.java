package com.burdew.app.sms;

import android.content.BroadcastReceiver;
import android.content.Context;
import android.content.Intent;
import android.provider.Telephony;
import android.telephony.SmsMessage;

import com.burdew.app.BurdewApp;
import com.burdew.app.data.BurdewRepository;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * Receives incoming SMS. Long messages arrive in parts, so parts are joined per sender
 * before parsing. The message is always stored before anything else happens, and the
 * broadcast is NOT aborted: the phone's normal SMS app still receives every message.
 */
public class SmsReceiver extends BroadcastReceiver {

    @Override
    public void onReceive(Context context, Intent intent) {
        if (!Telephony.Sms.Intents.SMS_RECEIVED_ACTION.equals(intent.getAction())) return;
        SmsMessage[] parts = Telephony.Sms.Intents.getMessagesFromIntent(intent);
        if (parts == null || parts.length == 0) return;

        Map<String, StringBuilder> bySender = new LinkedHashMap<>();
        for (SmsMessage p : parts) {
            String from = p.getOriginatingAddress() == null ? "" : p.getOriginatingAddress();
            StringBuilder sb = bySender.get(from);
            if (sb == null) bySender.put(from, sb = new StringBuilder());
            sb.append(p.getMessageBody());
        }

        BurdewRepository repo = BurdewApp.of(context).repo();
        final PendingResult pending = goAsync(); // DB work must not run on the main thread
        repo.runInBackground(() -> {
            try {
                for (Map.Entry<String, StringBuilder> e : bySender.entrySet()) {
                    BurdewRepository.SmsOutcome out = repo.handleIncomingSmsSync(e.getKey(), e.getValue().toString());
                    if (out.reply != null) SmsSender.send(context, repo, e.getKey(), out.reply);
                }
            } finally {
                pending.finish();
            }
        });
    }
}
