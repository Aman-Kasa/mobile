package com.burdew.app.ui.reports;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.LinearLayout;
import android.widget.Spinner;

import com.burdew.app.R;
import com.burdew.app.core.Money;
import com.burdew.app.core.PaymentStatus;
import com.burdew.app.core.RequestStatus;
import com.burdew.app.core.ServiceType;
import com.burdew.app.core.TransactionRules;
import com.burdew.app.data.DataExport;
import com.burdew.app.data.db.Daos.TxRow;
import com.burdew.app.ui.BaseActivity;
import com.burdew.app.ui.Ui;
import com.burdew.app.ui.requests.ReceiptPhotos;

import java.io.File;
import java.util.ArrayList;
import java.util.Calendar;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Daily / weekly / monthly reports (spec screen 14), computed locally.
 * Utility bill money is shown separately from BURDEW service income.
 */
public class ReportsActivity extends BaseActivity {

    private LinearLayout out;
    private List<TxRow> lastRows = new ArrayList<>();

    @Override
    protected void onReady(Bundle savedInstanceState) {
        setTitle(R.string.reports);
        LinearLayout f = Ui.form(this);
        Spinner period = Ui.spinner(f, R.string.period, new String[]{getString(R.string.today),
                getString(R.string.last_7_days), getString(R.string.this_month), getString(R.string.all_time)});
        Ui.button(f, R.string.export_csv, true, v -> exportCsv());
        out = Ui.column(this);
        f.addView(out);
        period.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) { load(pos); }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private long startOf(int period) {
        Calendar c = Calendar.getInstance();
        c.set(Calendar.HOUR_OF_DAY, 0);
        c.set(Calendar.MINUTE, 0);
        c.set(Calendar.SECOND, 0);
        c.set(Calendar.MILLISECOND, 0);
        switch (period) {
            case 0: return c.getTimeInMillis();
            case 1: c.add(Calendar.DAY_OF_YEAR, -6); return c.getTimeInMillis();
            case 2: c.set(Calendar.DAY_OF_MONTH, 1); return c.getTimeInMillis();
            default: return 0;
        }
    }

    private void load(int period) {
        long from = startOf(period);
        repo().transactionsBetween(from, Long.MAX_VALUE, session().providerFilter(), r -> {
            if (failed(r)) return;
            lastRows = r.value;
            render(r.value);
        });
        if (session().isAdmin()) {
            repo().outstandingBefore(Long.MAX_VALUE, r -> {
                if (r.ok()) Ui.text(out, getString(R.string.outstanding_requests, r.value));
            });
        }
    }

    private void render(List<TxRow> rows) {
        out.removeAllViews();
        long elecBills = 0, waterBills = 0, collected = 0, paid = 0, charges = 0, earnings = 0;
        int elec = 0, water = 0, completed = 0, cancelled = 0, voided = 0, pending = 0;
        Map<String, long[]> perProvider = new LinkedHashMap<>();
        for (TxRow t : rows) {
            if (t.voided) { voided++; continue; }
            boolean done = TransactionRules.earnsCompensation(PaymentStatus.valueOf(t.paymentStatus),
                    RequestStatus.valueOf(t.requestStatus), false);
            if (RequestStatus.CANCELLED.name().equals(t.requestStatus)) cancelled++;
            if (!done) { if (!RequestStatus.CANCELLED.name().equals(t.requestStatus)) pending++; continue; }
            completed++;
            if (ServiceType.WATER.name().equals(t.serviceType)) { water++; waterBills += t.billAmount; }
            else { elec++; elecBills += t.billAmount; }
            collected += t.amountCollected;
            paid += t.amountPaid;
            charges += t.serviceCharge;
            earnings += t.providerFee;
            String key = t.providerName == null ? getString(R.string.unassigned) : t.providerName;
            long[] agg = perProvider.get(key);
            if (agg == null) perProvider.put(key, agg = new long[2]);
            agg[0]++;
            agg[1] += t.providerFee;
        }

        LinearLayout a = Ui.card(this, out);
        Ui.section(a, R.string.completed_transactions);
        Ui.text(a, getString(R.string.report_completed, completed, elec, water));
        Ui.text(a, getString(R.string.report_not_complete, pending, cancelled, voided));

        LinearLayout b = Ui.card(this, out);
        Ui.section(b, R.string.utility_money);
        Ui.muted(b, getString(R.string.utility_money_hint));
        Ui.text(b, getString(R.string.electricity) + ": " + Money.format(elecBills));
        Ui.text(b, getString(R.string.water) + ": " + Money.format(waterBills));
        Ui.text(b, getString(R.string.amount_collected) + ": " + Money.format(collected));
        Ui.text(b, getString(R.string.amount_paid) + ": " + Money.format(paid));

        LinearLayout c = Ui.card(this, out);
        Ui.section(c, R.string.service_income);
        Ui.text(c, getString(R.string.service_charge) + ": " + Money.format(charges));
        Ui.text(c, getString(R.string.total_provider_earnings) + ": " + Money.format(earnings));

        if (!perProvider.isEmpty()) {
            LinearLayout d = Ui.card(this, out);
            Ui.section(d, R.string.provider_earnings);
            for (Map.Entry<String, long[]> e : perProvider.entrySet()) {
                Ui.text(d, getString(R.string.provider_earnings_line, e.getKey(), (int) e.getValue()[0], Money.format(e.getValue()[1])));
            }
        }
    }

    private void exportCsv() {
        List<TxRow> rows = lastRows;
        repo().runInBackground(() -> {
            try {
                File f = DataExport.transactionsCsv(this, rows);
                Uri uri = ReceiptPhotos.uriFor(this, f);
                repo().postMain(() -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                        .setType("text/csv").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), getString(R.string.export_csv))));
            } catch (Exception e) {
                repo().postMain(() -> Ui.toast(this, getString(R.string.export_failed, e.getMessage())));
            }
        });
    }
}
