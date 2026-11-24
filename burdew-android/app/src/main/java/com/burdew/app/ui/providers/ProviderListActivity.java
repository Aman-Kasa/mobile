package com.burdew.app.ui.providers;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.TextView;

import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.recyclerview.widget.RecyclerView;

import com.burdew.app.R;
import com.burdew.app.data.db.Entities.User;
import com.burdew.app.ui.BaseActivity;
import com.burdew.app.ui.RowAdapter;

/** Service provider management (spec screen 9). Admin only. */
public class ProviderListActivity extends BaseActivity {

    @Override
    protected void onReady(Bundle savedInstanceState) {
        if (!session().isAdmin()) { finish(); return; }
        setTitle(R.string.service_providers);
        setContentView(R.layout.activity_list);
        TextView empty = findViewById(R.id.empty);
        RowAdapter<User> adapter = new RowAdapter<>(u -> new String[]{
                u.fullName,
                "@" + u.username + (u.phoneNumber == null ? "" : " · " + u.phoneNumber),
                getString(u.active ? R.string.active : R.string.inactive)
        }, u -> startActivity(new Intent(this, ProviderEditActivity.class).putExtra(ProviderEditActivity.EXTRA_ID, u.userId)));
        RecyclerView list = findViewById(R.id.list);
        list.setLayoutManager(new LinearLayoutManager(this));
        list.setAdapter(adapter);
        View add = findViewById(R.id.add);
        add.setVisibility(View.VISIBLE);
        add.setOnClickListener(v -> startActivity(new Intent(this, ProviderEditActivity.class)));
        repo().db().users().providers().observe(this, rows -> {
            adapter.submit(rows);
            empty.setVisibility(rows.isEmpty() ? View.VISIBLE : View.GONE);
        });
    }
}
