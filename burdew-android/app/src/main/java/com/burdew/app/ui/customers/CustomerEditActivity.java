package com.burdew.app.ui.customers;

import android.content.Intent;
import android.os.Bundle;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;

import com.burdew.app.R;
import com.burdew.app.core.Money;
import com.burdew.app.core.ServiceType;
import com.burdew.app.data.BurdewRepository;
import com.burdew.app.data.db.Entities.Customer;
import com.burdew.app.ui.BaseActivity;
import com.burdew.app.ui.LocationCapture;
import com.burdew.app.ui.Ui;
import com.burdew.app.ui.requests.RequestDetailActivity;
import com.burdew.app.ui.requests.RequestEditActivity;

/** Customer registration and details (spec screens 5 and 8). */
public class CustomerEditActivity extends BaseActivity {

    public static final String EXTRA_ID = "customerId";

    private Customer customer;
    private EditText name, phone, alt, address, account, notes;
    private Spinner service;
    private LocationCapture location;

    @Override
    protected void onReady(Bundle savedInstanceState) {
        String id = getIntent().getStringExtra(EXTRA_ID);
        setTitle(id == null ? R.string.new_customer : R.string.customer);
        LinearLayout f = Ui.form(this);
        name = Ui.field(f, R.string.full_name, Ui.TEXT);
        phone = Ui.field(f, R.string.phone, Ui.PHONE);
        alt = Ui.field(f, R.string.alt_phone, Ui.PHONE);
        address = Ui.field(f, R.string.address, Ui.MULTILINE);
        service = Ui.spinner(f, R.string.service_type,
                new String[]{getString(R.string.electricity), getString(R.string.water)});
        account = Ui.field(f, R.string.account_reference, Ui.PLAIN);
        notes = Ui.field(f, R.string.additional_info, Ui.MULTILINE);
        location = new LocationCapture(this, f);
        Ui.button(f, R.string.save, false, v -> save());

        if (id == null) {
            customer = new Customer();
            return;
        }
        LinearLayout history = Ui.column(this);
        f.addView(history);
        repo().getCustomer(id, r -> {
            if (failed(r) || r.value == null) { finish(); return; }
            customer = r.value;
            name.setText(customer.fullName);
            phone.setText(customer.phoneNumber);
            alt.setText(customer.alternativePhone);
            address.setText(customer.address);
            account.setText(customer.accountReference);
            notes.setText(customer.notes);
            service.setSelection(customer.defaultService == ServiceType.WATER ? 1 : 0);
            location.set(customer.latitude, customer.longitude);
            buildHistory(history);
        });
    }

    private void buildHistory(LinearLayout h) {
        if (session().isAdmin()) {
            Ui.button(h, R.string.new_request_for_customer, true, v -> startActivity(
                    new Intent(this, RequestEditActivity.class).putExtra(RequestEditActivity.EXTRA_CUSTOMER, customer.customerId)));
        }
        Ui.section(h, R.string.previous_requests);
        LinearLayout reqs = Ui.column(this);
        h.addView(reqs);
        repo().db().requests().forCustomer(customer.customerId).observe(this, rows -> {
            reqs.removeAllViews();
            if (rows.isEmpty()) Ui.muted(reqs, getString(R.string.empty_list));
            for (com.burdew.app.data.db.Daos.RequestRow r : rows) {
                TextView t = Ui.text(reqs, r.requestCode + " · " + Ui.label(r.serviceType) + " · "
                        + Ui.label(r.requestStatus) + " · " + Ui.date(r.createdAt));
                t.setPadding(0, Ui.dp(this, 6), 0, Ui.dp(this, 6));
                t.setOnClickListener(v -> startActivity(new Intent(this, RequestDetailActivity.class)
                        .putExtra(RequestDetailActivity.EXTRA_ID, r.requestId)));
            }
        });
        Ui.section(h, R.string.transaction_history);
        LinearLayout txs = Ui.column(this);
        h.addView(txs);
        repo().db().transactions().forCustomer(customer.customerId).observe(this, rows -> {
            txs.removeAllViews();
            if (rows.isEmpty()) Ui.muted(txs, getString(R.string.empty_list));
            for (com.burdew.app.data.db.Daos.TxRow t : rows) {
                Ui.text(txs, Ui.date(t.transactionDate) + " · " + Ui.label(t.serviceType) + " · "
                        + Money.format(t.billAmount) + " · " + Ui.label(t.paymentStatus) + (t.voided ? " · VOID" : ""));
            }
        });
        if (session().isAdmin()) {
            Ui.button(h, R.string.archive_customer, true, v -> new AlertDialog.Builder(this)
                    .setMessage(R.string.archive_confirm)
                    .setPositiveButton(R.string.archive_customer, (d, w) -> repo().archiveCustomer(customer.customerId, r -> {
                        if (!failed(r)) finish();
                    }))
                    .setNegativeButton(android.R.string.cancel, null).show());
        }
    }

    private void save() {
        if (customer == null) return;
        boolean isNew = customer.customerId.isEmpty();
        customer.fullName = Ui.val(name);
        customer.phoneNumber = Ui.val(phone);
        customer.alternativePhone = BurdewRepository.nullIfBlank(Ui.val(alt));
        customer.address = BurdewRepository.nullIfBlank(Ui.val(address));
        customer.accountReference = BurdewRepository.nullIfBlank(Ui.val(account));
        customer.notes = BurdewRepository.nullIfBlank(Ui.val(notes));
        customer.defaultService = service.getSelectedItemPosition() == 1 ? ServiceType.WATER : ServiceType.ELECTRICITY;
        customer.latitude = location.lat;
        customer.longitude = location.lon;
        repo().saveCustomer(customer, isNew, r -> {
            if (failed(r)) return;
            if (location.source != null && location.lat != null) {
                repo().recordLocation(r.value.customerId, null, location.lat, location.lon, location.accuracy, location.source);
            }
            Ui.toast(this, getString(R.string.saved));
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        location.onActivityResult(requestCode, resultCode, data);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(requestCode, p, g);
        location.onPermissionResult(requestCode);
    }

    @Override
    protected void onDestroy() {
        if (location != null) location.stop();
        super.onDestroy();
    }
}
