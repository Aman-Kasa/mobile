package com.burdew.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.MenuItem;

import androidx.appcompat.app.AppCompatActivity;

import com.burdew.app.BurdewApp;
import com.burdew.app.data.BurdewRepository;
import com.burdew.app.data.Session;

/** Every screen except splash/login requires a signed-in operator. */
public abstract class BaseActivity extends AppCompatActivity {

    protected boolean requiresLogin() { return true; }

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        if (requiresLogin() && !session().isSignedIn()) {
            // Process was killed and restarted on this screen: go back to login.
            Intent i = new Intent(this, LoginActivity.class);
            i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
            startActivity(i);
            finish();
            return;
        }
        if (getSupportActionBar() != null && !isTaskRoot()) getSupportActionBar().setDisplayHomeAsUpEnabled(true);
        onReady(savedInstanceState);
    }

    /** Called instead of onCreate body, only when the user is allowed here. */
    protected abstract void onReady(Bundle savedInstanceState);

    protected BurdewRepository repo() { return BurdewApp.of(this).repo(); }

    protected Session session() { return BurdewApp.of(this).session(); }

    protected <T> boolean failed(BurdewRepository.Result<T> r) {
        if (r.ok()) return false;
        Ui.toast(this, r.error);
        return true;
    }

    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        if (item.getItemId() == android.R.id.home) {
            finish();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }
}
