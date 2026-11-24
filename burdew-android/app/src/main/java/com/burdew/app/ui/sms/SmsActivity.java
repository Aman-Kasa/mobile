package com.burdew.app.ui.sms;

import android.Manifest;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.EditText;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.burdew.app.R;
import com.burdew.app.core.PhoneNumbers;
import com.burdew.app.core.SmsRequestParser;
import com.burdew.app.data.db.Entities.Customer;
import com.burdew.app.data.db.Entities.SmsMessage;
import com.burdew.app.sms.SmsSender;
import com.burdew.app.ui.BaseActivity;
import com.burdew.app.ui.RowAdapter;
import com.burdew.app.ui.Ui;
import com.burdew.app.ui.requests.RequestDetailActivity;
import com.burdew.app.ui.requests.RequestEditActivity;

import java.util.List;

/**
 * SMS management (spec screen 13). Messages that failed automatic parsing can be
 * turned into a request by hand. Nothing is ever deleted here.
 */
public class SmsActivity extends BaseActivity {

    private static final String[] FILTERS = {null, "FAILED_PARSE", "RECEIVED", "PROCESSED", "DUPLICATE", "IGNORED",
            "SENT", "DELIVERED", "SEND_FAILED"};
    private static final int SMS_PERMISSION = 8101;

    private LiveData<List<SmsMessage>> current;
    private RowAdapter<SmsMessage> adapter;
    private TextView empty;

    @Override
    protected void onReady(Bundle savedInstanceState) {
        if (!session().isAdmin()) { finish(); return; }
        setTitle(R.string.sms_messages);
        setContentView(R.layout.activity_list);
        empty = findViewById(R.id.empty);

        String[] labels = new String[FILTERS.length];
        labels[0] = getString(R.string.all_messages);
        for (int i = 1; i < FILTERS.length; i++) labels[i] = Ui.label(FILTERS[i]);
        Spinner filter = findViewById(R.id.filter);
        filter.setVisibility(View.VISIBLE);
        ArrayAdapter<String> fa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        fa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filter.setAdapter(fa);
        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { query(FILTERS[pos]); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });

        adapter = new RowAdapter<>(m -> new String[]{
                ("OUT".equals(m.messageType) ? "→ " : "← ") + m.phoneNumber + " · " + Ui.dateTime(m.receivedAt),
                m.messageBody + (m.error == null ? "" : "\n⚠ " + m.error),
                Ui.label(m.processingStatus)
        }, this::open);
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        // FAB = compose a message to a customer.
        View add = findViewById(R.id.add);
        add.setVisibility(View.VISIBLE);
        add.setOnClickListener(v -> compose(null));

        askPermissions();
    }

    private void askPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.RECEIVE_SMS) != PackageManager.PERMISSION_GRANTED
                || ContextCompat.checkSelfPermission(this, Manifest.permission.SEND_SMS) != PackageManager.PERMISSION_GRANTED) {
            new AlertDialog.Builder(this).setTitle(R.string.sms_permission_title)
                    .setMessage(R.string.sms_permission_body)
                    .setPositiveButton(android.R.string.ok, (d, w) -> ActivityCompat.requestPermissions(this,
                            new String[]{Manifest.permission.RECEIVE_SMS, Manifest.permission.SEND_SMS}, SMS_PERMISSION))
                    .setNegativeButton(R.string.not_now, null).show();
        }
    }

    private void query(String status) {
        if (current != null) current.removeObservers(this);
        current = repo().db().sms().list(status);
        current.observe(this, rows -> {
            adapter.submit(rows);
            empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }

    private void open(SmsMessage m) {
        if (m.relatedRequestId != null) {
            startActivity(new Intent(this, RequestDetailActivity.class).putExtra(RequestDetailActivity.EXTRA_ID, m.relatedRequestId));
            return;
        }
        if (!"IN".equals(m.messageType)) { compose(m.phoneNumber); return; }
        new AlertDialog.Builder(this).setTitle(m.phoneNumber).setMessage(m.messageBody)
                .setPositiveButton(R.string.create_request_manually, (d, w) -> manualRequest(m))
                .setNeutralButton(R.string.reply, (d, w) -> compose(m.phoneNumber))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    /** Prefills a request from whatever the parser could read; the operator completes the rest. */
    private void manualRequest(SmsMessage m) {
        SmsRequestParser.Result p = SmsRequestParser.parse(m.messageBody, m.phoneNumber);
        String phone = p.phone != null ? p.phone : PhoneNumbers.normalize(m.phoneNumber);
        repo().runInBackground(() -> {
            Customer c = repo().db().customers().findByPhone(phone);
            repo().postMain(() -> {
                Intent i = new Intent(this, RequestEditActivity.class)
                        .putExtra(RequestEditActivity.EXTRA_SMS_ID, m.messageId)
                        .putExtra(RequestEditActivity.EXTRA_PREFILL_ACCOUNT, p.account)
                        .putExtra(RequestEditActivity.EXTRA_PREFILL_NOTE, getString(R.string.from_sms, m.messageBody));
                if (c != null) i.putExtra(RequestEditActivity.EXTRA_CUSTOMER, c.customerId);
                else Ui.toast(this, getString(R.string.register_customer_first, phone));
                startActivity(i);
            });
        });
    }

    private void compose(String to) {
        android.widget.LinearLayout box = Ui.column(this);
        int pad = Ui.dp(this, 16);
        box.setPadding(pad, pad, pad, 0);
        EditText phone = Ui.field(box, R.string.phone, Ui.PHONE);
        EditText body = Ui.field(box, R.string.message, Ui.MULTILINE);
        if (to != null) phone.setText(to);
        new AlertDialog.Builder(this).setTitle(R.string.send_sms).setView(box)
                .setPositiveButton(R.string.send, (d, w) -> {
                    String number = PhoneNumbers.normalize(Ui.val(phone));
                    if (!PhoneNumbers.isPlausible(number) || Ui.val(body).isEmpty()) {
                        Ui.toast(this, getString(R.string.invalid_sms));
                        return;
                    }
                    String text = Ui.val(body);
                    repo().runInBackground(() -> SmsSender.send(getApplicationContext(), repo(), number, text));
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }
}
