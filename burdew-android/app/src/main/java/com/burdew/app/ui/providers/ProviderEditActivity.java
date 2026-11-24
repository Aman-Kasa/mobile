package com.burdew.app.ui.providers;

import android.content.Intent;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.burdew.app.R;
import com.burdew.app.core.Money;
import com.burdew.app.ui.BaseActivity;
import com.burdew.app.ui.Ui;
import com.burdew.app.ui.requests.RequestDetailActivity;

import java.util.Arrays;

/** Register / edit a provider, activate or deactivate, and see their work and earnings. */
public class ProviderEditActivity extends BaseActivity {

    public static final String EXTRA_ID = "userId";

    @Override
    protected void onReady(Bundle savedInstanceState) {
        if (!session().isAdmin()) { finish(); return; }
        String id = getIntent().getStringExtra(EXTRA_ID);
        setTitle(id == null ? R.string.new_provider : R.string.provider);
        LinearLayout f = Ui.form(this);
        EditText name = Ui.field(f, R.string.full_name, Ui.TEXT);
        EditText phone = Ui.field(f, R.string.phone, Ui.PHONE);
        EditText username = Ui.field(f, R.string.username, Ui.PLAIN);
        EditText password = Ui.field(f, id == null ? R.string.password : R.string.new_password_optional, Ui.PASSWORD);
        CheckBox active = new CheckBox(this);
        active.setText(R.string.active);
        active.setChecked(true);
        f.addView(active);

        Ui.button(f, R.string.save, false, v -> {
            char[] pw = Ui.val(password).toCharArray();
            if (id == null) {
                repo().createProvider(Ui.val(name), Ui.val(phone), Ui.val(username), pw, r -> {
                    Arrays.fill(pw, '\0');
                    if (!failed(r)) { Ui.toast(this, getString(R.string.saved)); finish(); }
                });
            } else {
                repo().updateProvider(id, Ui.val(name), Ui.val(phone), active.isChecked(), pw, r -> {
                    Arrays.fill(pw, '\0');
                    if (!failed(r)) { Ui.toast(this, getString(R.string.saved)); finish(); }
                });
            }
        });

        if (id == null) return;
        username.setEnabled(false);
        repo().getUser(id, r -> {
            if (failed(r) || r.value == null) { finish(); return; }
            name.setText(r.value.fullName);
            phone.setText(r.value.phoneNumber);
            username.setText(r.value.username);
            active.setChecked(r.value.active);
        });

        Ui.section(f, R.string.performance);
        TextView done = Ui.text(f, "");
        TextView earned = Ui.text(f, "");
        repo().db().transactions().completedCount(id).observe(this, n -> done.setText(getString(R.string.completed_count, n)));
        repo().db().transactions().earnings(id).observe(this, v -> earned.setText(getString(R.string.earnings_line, Money.format(v))));

        Ui.section(f, R.string.assigned_requests);
        LinearLayout reqs = Ui.column(this);
        f.addView(reqs);
        repo().db().requests().list(null, id).observe(this, rows -> {
            reqs.removeAllViews();
            if (rows.isEmpty()) Ui.muted(reqs, getString(R.string.empty_list));
            for (com.burdew.app.data.db.Daos.RequestRow row : rows) {
                TextView t = Ui.text(reqs, row.requestCode + " · " + row.customerName + " · " + Ui.label(row.requestStatus));
                t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
                t.setOnClickListener(v -> startActivity(new Intent(this, RequestDetailActivity.class)
                        .putExtra(RequestDetailActivity.EXTRA_ID, row.requestId)));
            }
        });
    }
}
