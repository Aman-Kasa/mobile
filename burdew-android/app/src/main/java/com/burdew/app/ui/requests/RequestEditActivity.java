package com.burdew.app.ui.requests;

import android.content.Intent;
import android.os.Bundle;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.annotation.NonNull;

import com.burdew.app.R;
import com.burdew.app.core.ServiceType;
import com.burdew.app.data.BurdewRepository;
import com.burdew.app.data.db.Entities.Customer;
import com.burdew.app.data.db.Entities.ServiceRequest;
import com.burdew.app.data.db.Entities.User;
import com.burdew.app.ui.BaseActivity;
import com.burdew.app.ui.LocationCapture;
import com.burdew.app.ui.Ui;
import com.burdew.app.ui.customers.CustomerListActivity;

import java.util.ArrayList;
import java.util.List;

/** Service request creation (spec screen 6). Admin only. */
public class RequestEditActivity extends BaseActivity {

    public static final String EXTRA_CUSTOMER = "customerId";
    public static final String EXTRA_SMS_ID = "smsId";
    public static final String EXTRA_PREFILL_ACCOUNT = "account";
    public static final String EXTRA_PREFILL_NOTE = "note";
    private static final int PICK_CUSTOMER = 6101;

    private Customer customer;
    private TextView customerLabel;
    private EditText account, address, instructions;
    private Spinner service, priority, provider;
    private LocationCapture location;
    private final List<User> providers = new ArrayList<>();
    private Button save;

    @Override
    protected void onReady(Bundle savedInstanceState) {
        if (!session().isAdmin()) { finish(); return; }
        setTitle(R.string.new_request);
        LinearLayout f = Ui.form(this);
        Ui.section(f, R.string.customer);
        customerLabel = Ui.text(f, getString(R.string.no_customer_selected));
        Ui.button(f, R.string.choose_customer, true, v -> startActivityForResult(
                new Intent(this, CustomerListActivity.class).putExtra(CustomerListActivity.EXTRA_PICK, true), PICK_CUSTOMER));

        Ui.section(f, R.string.request_details);
        service = Ui.spinner(f, R.string.service_type, new String[]{getString(R.string.electricity), getString(R.string.water)});
        account = Ui.field(f, R.string.account_reference, Ui.PLAIN);
        address = Ui.field(f, R.string.address, Ui.MULTILINE);
        instructions = Ui.field(f, R.string.additional_instructions, Ui.MULTILINE);
        priority = Ui.spinner(f, R.string.priority, new String[]{getString(R.string.priority_normal),
                getString(R.string.priority_high), getString(R.string.priority_urgent)});
        provider = Ui.spinner(f, R.string.assign_provider, new String[]{getString(R.string.unassigned)});
        location = new LocationCapture(this, f);
        save = Ui.button(f, R.string.create_request, false, v -> save());

        account.setText(getIntent().getStringExtra(EXTRA_PREFILL_ACCOUNT));
        instructions.setText(getIntent().getStringExtra(EXTRA_PREFILL_NOTE));

        repo().activeProviders(r -> {
            if (failed(r)) return;
            providers.clear();
            providers.addAll(r.value);
            String[] names = new String[providers.size() + 1];
            names[0] = getString(R.string.unassigned);
            for (int i = 0; i < providers.size(); i++) names[i + 1] = providers.get(i).fullName;
            android.widget.ArrayAdapter<String> a = new android.widget.ArrayAdapter<>(this, android.R.layout.simple_spinner_item, names);
            a.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
            provider.setAdapter(a);
        });

        String cid = getIntent().getStringExtra(EXTRA_CUSTOMER);
        if (cid != null) loadCustomer(cid);
    }

    private void loadCustomer(String id) {
        repo().getCustomer(id, r -> {
            if (failed(r) || r.value == null) return;
            customer = r.value;
            customerLabel.setText(customer.fullName + " · " + customer.phoneNumber);
            if (Ui.val(account).isEmpty() && customer.accountReference != null) account.setText(customer.accountReference);
            if (Ui.val(address).isEmpty() && customer.address != null) address.setText(customer.address);
            if (customer.defaultService != null) service.setSelection(customer.defaultService == ServiceType.WATER ? 1 : 0);
            if (location.lat == null && customer.hasLocation()) location.set(customer.latitude, customer.longitude);
        });
    }

    private void save() {
        if (customer == null) { Ui.toast(this, getString(R.string.choose_customer_first)); return; }
        ServiceRequest r = new ServiceRequest();
        r.customerId = customer.customerId;
        r.serviceType = service.getSelectedItemPosition() == 1 ? ServiceType.WATER : ServiceType.ELECTRICITY;
        r.accountReference = BurdewRepository.nullIfBlank(Ui.val(account));
        r.address = BurdewRepository.nullIfBlank(Ui.val(address));
        r.description = BurdewRepository.nullIfBlank(Ui.val(instructions));
        r.priority = priority.getSelectedItemPosition();
        int p = provider.getSelectedItemPosition();
        r.assignedProviderId = p > 0 && p <= providers.size() ? providers.get(p - 1).userId : null;
        r.latitude = location.lat;
        r.longitude = location.lon;
        String smsId = getIntent().getStringExtra(EXTRA_SMS_ID);
        if (smsId != null) r.source = "SMS";
        save.setEnabled(false);
        repo().createRequest(r, res -> {
            save.setEnabled(true);
            if (failed(res)) return;
            if (location.source != null && location.lat != null) {
                repo().recordLocation(customer.customerId, res.value.requestId, location.lat, location.lon, location.accuracy, location.source);
            }
            if (smsId != null) repo().markSmsProcessed(smsId, res.value.requestId, null);
            Ui.toast(this, getString(R.string.request_created, res.value.requestCode));
            startActivity(new Intent(this, RequestDetailActivity.class).putExtra(RequestDetailActivity.EXTRA_ID, res.value.requestId));
            finish();
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == PICK_CUSTOMER && resultCode == RESULT_OK && data != null) {
            loadCustomer(data.getStringExtra(CustomerListActivity.RESULT_ID));
            return;
        }
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
