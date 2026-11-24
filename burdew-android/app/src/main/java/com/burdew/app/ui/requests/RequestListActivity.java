package com.burdew.app.ui.requests;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.AdapterView;
import android.widget.ArrayAdapter;
import android.widget.Spinner;
import android.widget.TextView;

import androidx.lifecycle.LiveData;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.burdew.app.R;
import com.burdew.app.core.RequestStatus;
import com.burdew.app.data.db.Daos.RequestRow;
import com.burdew.app.ui.BaseActivity;
import com.burdew.app.ui.RowAdapter;
import com.burdew.app.ui.Ui;

import java.util.List;

/** Request management (spec screen 10). Providers see only requests assigned to them. */
public class RequestListActivity extends BaseActivity {

    private LiveData<List<RequestRow>> current;
    private RowAdapter<RequestRow> adapter;
    private TextView empty;

    @Override
    protected void onReady(Bundle savedInstanceState) {
        setTitle(session().isAdmin() ? R.string.requests : R.string.my_requests);
        setContentView(R.layout.activity_list);
        empty = findViewById(R.id.empty);

        RequestStatus[] statuses = RequestStatus.values();
        String[] labels = new String[statuses.length + 1];
        labels[0] = getString(R.string.all_statuses);
        for (int i = 0; i < statuses.length; i++) labels[i + 1] = Ui.label(statuses[i]);
        Spinner filter = findViewById(R.id.filter);
        filter.setVisibility(View.VISIBLE);
        ArrayAdapter<String> fa = new ArrayAdapter<>(this, android.R.layout.simple_spinner_item, labels);
        fa.setDropDownViewResource(android.R.layout.simple_spinner_dropdown_item);
        filter.setAdapter(fa);

        String[] priorities = {null, getString(R.string.priority_high), getString(R.string.priority_urgent)};
        adapter = new RowAdapter<>(r -> new String[]{
                r.requestCode + " · " + r.customerName,
                Ui.label(r.serviceType) + " · " + (r.address == null ? r.customerPhone : r.address)
                        + (r.providerName == null ? "" : " · " + r.providerName)
                        + (r.priority > 0 && r.priority < priorities.length ? " · " + priorities[r.priority] : ""),
                Ui.label(r.requestStatus)
        }, r -> startActivity(new Intent(this, RequestDetailActivity.class).putExtra(RequestDetailActivity.EXTRA_ID, r.requestId)));
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);

        if (session().isAdmin()) {
            View add = findViewById(R.id.add);
            add.setVisibility(View.VISIBLE);
            add.setOnClickListener(v -> startActivity(new Intent(this, RequestEditActivity.class)));
        }

        filter.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener() {
            @Override public void onItemSelected(AdapterView<?> p, View v, int pos, long id) {
                query(pos == 0 ? null : statuses[pos - 1].name());
            }
            @Override public void onNothingSelected(AdapterView<?> p) {}
        });
    }

    private void query(String status) {
        if (current != null) current.removeObservers(this);
        current = repo().db().requests().list(status, session().providerFilter());
        current.observe(this, rows -> {
            adapter.submit(rows);
            empty.setVisibility(rows == null || rows.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }
}
