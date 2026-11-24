package com.burdew.app.ui.customers;

import android.content.Intent;
import android.os.Bundle;
import android.text.Editable;
import android.text.TextWatcher;
import android.view.View;
import android.widget.EditText;
import android.widget.TextView;

import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.burdew.app.R;
import com.burdew.app.data.db.Entities.Customer;
import com.burdew.app.ui.BaseActivity;
import com.burdew.app.ui.RowAdapter;

import java.util.List;

/** Customer directory (spec screen 8). With EXTRA_PICK it returns the chosen customer. */
public class CustomerListActivity extends BaseActivity {

    public static final String EXTRA_PICK = "pick";
    public static final String RESULT_ID = "customerId";

    private LiveData<List<Customer>> current;
    private RowAdapter<Customer> adapter;
    private TextView empty;

    @Override
    protected void onReady(Bundle savedInstanceState) {
        boolean pick = getIntent().getBooleanExtra(EXTRA_PICK, false);
        setTitle(pick ? R.string.choose_customer : R.string.customers);
        setContentView(R.layout.activity_list);
        empty = findViewById(R.id.empty);
        EditText search = findViewById(R.id.search);
        search.setVisibility(View.VISIBLE);
        search.setHint(R.string.search_customers);

        adapter = new RowAdapter<>(c -> new String[]{
                c.fullName,
                c.phoneNumber + (c.address == null ? "" : " · " + c.address)
                        + (c.accountReference == null ? "" : " · #" + c.accountReference),
                c.hasLocation() ? getString(R.string.badge_gps) : null
        }, c -> {
            if (pick) {
                setResult(RESULT_OK, new Intent().putExtra(RESULT_ID, c.customerId));
                finish();
            } else {
                startActivity(new Intent(this, CustomerEditActivity.class).putExtra(CustomerEditActivity.EXTRA_ID, c.customerId));
            }
        });
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        View add = findViewById(R.id.add);
        add.setVisibility(View.VISIBLE);
        add.setOnClickListener(v -> startActivity(new Intent(this, CustomerEditActivity.class)));

        search.addTextChangedListener(new TextWatcher() {
            @Override public void beforeTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void onTextChanged(CharSequence s, int a, int b, int c) {}
            @Override public void afterTextChanged(Editable s) { query(s.toString().trim()); }
        });
        query("");
    }

    private void query(String q) {
        if (current != null) current.removeObservers(this);
        current = repo().db().customers().search(q);
        current.observe(this, rows -> {
            adapter.submit(rows);
            empty.setVisibility(rows == null || rows.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }
}
