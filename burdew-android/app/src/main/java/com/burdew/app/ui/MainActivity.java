package com.burdew.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.view.Menu;
import android.view.MenuItem;
import android.widget.GridLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.lifecycle.LiveData;

import com.burdew.app.R;
import com.burdew.app.core.Money;
import com.burdew.app.core.RequestStatus;
import com.burdew.app.data.db.BurdewDatabase;
import com.burdew.app.ui.customers.CustomerListActivity;
import com.burdew.app.ui.map.MapActivity;
import com.burdew.app.ui.providers.ProviderListActivity;
import com.burdew.app.ui.reports.ReportsActivity;
import com.burdew.app.ui.requests.RequestDetailActivity;
import com.burdew.app.ui.requests.RequestEditActivity;
import com.burdew.app.ui.requests.RequestListActivity;
import com.burdew.app.ui.settings.SettingsActivity;
import com.burdew.app.ui.sms.SmsActivity;

/** Dashboard (spec screen 4). Providers see only their own numbers. */
public class MainActivity extends BaseActivity {

    @Override
    protected void onReady(Bundle savedInstanceState) {
        setTitle(R.string.dashboard);
        boolean admin = session().isAdmin();
        String mine = session().providerFilter();
        BurdewDatabase db = repo().db();

        LinearLayout f = Ui.form(this);
        Ui.muted(f, getString(R.string.signed_in_as, session().fullName(),
                getString(admin ? R.string.role_admin : R.string.role_provider)));

        GridLayout grid = new GridLayout(this);
        grid.setColumnCount(2);
        f.addView(grid);
        if (admin) stat(grid, R.string.total_customers, db.customers().count());
        stat(grid, R.string.status_new, db.requests().countByStatus(RequestStatus.NEW.name(), mine));
        stat(grid, R.string.status_pending, db.requests().countByStatus(RequestStatus.PENDING.name(), mine));
        stat(grid, R.string.status_assigned, db.requests().countByStatus(RequestStatus.ASSIGNED.name(), mine));
        stat(grid, R.string.status_in_progress, db.requests().countByStatus(RequestStatus.IN_PROGRESS.name(), mine));
        stat(grid, R.string.completed_transactions, db.transactions().completedCount(mine));
        TextView earn = stat(grid, admin ? R.string.total_provider_earnings : R.string.my_earnings, null);
        db.transactions().earnings(mine).observe(this, v -> earn.setText(Money.format(v == null ? 0 : v)));
        if (admin) stat(grid, R.string.sms_need_attention, db.sms().needsAttention());

        Ui.section(f, R.string.quick_actions);
        if (admin) Ui.button(f, R.string.new_request, false, v -> open(RequestEditActivity.class));
        Ui.button(f, admin ? R.string.requests : R.string.my_requests, !admin, v -> open(RequestListActivity.class));
        Ui.button(f, R.string.customers, true, v -> open(CustomerListActivity.class));
        if (admin) Ui.button(f, R.string.service_providers, true, v -> open(ProviderListActivity.class));
        Ui.button(f, R.string.offline_map, true, v -> open(MapActivity.class));
        Ui.button(f, R.string.reports, true, v -> open(ReportsActivity.class));
        if (admin) Ui.button(f, R.string.sms_messages, true, v -> open(SmsActivity.class));

        Ui.section(f, R.string.recent_activity);
        LinearLayout recent = Ui.column(this);
        f.addView(recent);
        db.requests().recent().observe(this, rows -> {
            recent.removeAllViews();
            if (rows == null || rows.isEmpty()) {
                Ui.muted(recent, getString(R.string.empty_list));
                return;
            }
            for (com.burdew.app.data.db.Daos.RequestRow r : rows) {
                if (!admin && r.providerName == null) continue;
                TextView t = Ui.text(recent, r.requestCode + " · " + r.customerName + " · " + Ui.label(r.requestStatus));
                t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
                t.setOnClickListener(v -> startActivity(new Intent(this, RequestDetailActivity.class)
                        .putExtra(RequestDetailActivity.EXTRA_ID, r.requestId)));
            }
        });
    }

    private TextView stat(GridLayout grid, int label, LiveData<Integer> source) {
        LinearLayout card = Ui.column(this);
        card.setBackgroundResource(R.drawable.bg_card);
        int p = Ui.dp(this, 12);
        card.setPadding(p, p, p, p);
        TextView value = new TextView(this);
        value.setTextSize(22);
        value.setTextColor(getResources().getColor(R.color.burdew_primary));
        value.setTypeface(value.getTypeface(), android.graphics.Typeface.BOLD);
        value.setText("–");
        card.addView(value);
        TextView l = new TextView(this);
        l.setText(label);
        l.setTextColor(getResources().getColor(R.color.burdew_text_muted));
        l.setTextSize(12);
        card.addView(l);
        GridLayout.LayoutParams lp = new GridLayout.LayoutParams(
                GridLayout.spec(GridLayout.UNDEFINED, 1f), GridLayout.spec(GridLayout.UNDEFINED, 1f));
        lp.width = 0;
        lp.setMargins(Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4), Ui.dp(this, 4));
        lp.setGravity(Gravity.FILL_HORIZONTAL);
        grid.addView(card, lp);
        if (source != null) source.observe(this, v -> value.setText(String.valueOf(v == null ? 0 : v)));
        return value;
    }

    private void open(Class<?> c) { startActivity(new Intent(this, c)); }

    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        menu.add(0, 1, 0, R.string.settings);
        menu.add(0, 2, 0, R.string.sign_out);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == 1) { open(SettingsActivity.class); return true; }
        if (item.getItemId() == 2) {
            session().signOut();
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
