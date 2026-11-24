package com.burdew.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import com.burdew.app.R;

import java.util.Arrays;

/**
 * Local sign-in, no cloud. On the very first launch there are no accounts, so this
 * screen creates the first administrator instead.
 */
public class LoginActivity extends BaseActivity {

    private boolean setupMode;
    private EditText name, username, password, confirm;
    private TextView title;
    private Button go;

    @Override protected boolean requiresLogin() { return false; }

    @Override
    protected void onReady(Bundle savedInstanceState) {
        setTitle(R.string.app_name);
        LinearLayout f = Ui.form(this);
        title = Ui.section(f, R.string.sign_in);
        name = Ui.field(f, R.string.full_name, Ui.TEXT);
        username = Ui.field(f, R.string.username, Ui.PLAIN);
        password = Ui.field(f, R.string.password, Ui.PASSWORD);
        confirm = Ui.field(f, R.string.confirm_password, Ui.PASSWORD);
        go = Ui.button(f, R.string.sign_in, false, v -> submit());
        go.setEnabled(false);
        show(false);
        repo().hasUsers(r -> {
            if (failed(r)) return;
            setupMode = !r.value;
            show(setupMode);
            go.setEnabled(true);
        });
    }

    private void show(boolean setup) {
        int vis = setup ? View.VISIBLE : View.GONE;
        ((View) name.getParent().getParent()).setVisibility(vis);
        ((View) confirm.getParent().getParent()).setVisibility(vis);
        title.setText(setup ? R.string.create_admin : R.string.sign_in);
        go.setText(setup ? R.string.create_admin : R.string.sign_in);
    }

    private void submit() {
        char[] pw = Ui.val(password).toCharArray();
        go.setEnabled(false);
        if (setupMode) {
            if (!Ui.val(password).equals(Ui.val(confirm))) {
                Ui.toast(this, getString(R.string.passwords_differ));
                go.setEnabled(true);
                return;
            }
            repo().createFirstAdmin(Ui.val(name), Ui.val(username), pw, r -> after(r.ok(), r.error, pw));
        } else {
            repo().login(Ui.val(username), pw, r -> after(r.ok(), r.error, pw));
        }
    }

    private void after(boolean ok, String error, char[] pw) {
        Arrays.fill(pw, '\0');
        go.setEnabled(true);
        if (!ok) {
            Ui.toast(this, error);
            return;
        }
        Intent i = new Intent(this, MainActivity.class);
        i.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK);
        startActivity(i);
        finish();
    }
}
