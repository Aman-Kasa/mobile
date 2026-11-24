package com.burdew.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.widget.LinearLayout;

import com.burdew.app.R;
import com.burdew.app.config.AppConfig;

/** Four short explanations, shown once. */
public class OnboardingActivity extends BaseActivity {

    @Override protected boolean requiresLogin() { return false; }

    @Override
    protected void onReady(Bundle savedInstanceState) {
        setTitle(R.string.welcome);
        LinearLayout f = Ui.form(this);
        int[][] pages = {
                {R.string.onb_what_title, R.string.onb_what_body},
                {R.string.onb_how_title, R.string.onb_how_body},
                {R.string.onb_map_title, R.string.onb_map_body},
                {R.string.onb_privacy_title, R.string.onb_privacy_body},
        };
        for (int[] p : pages) {
            LinearLayout card = Ui.card(this, f);
            Ui.section(card, p[0]);
            Ui.text(card, getString(p[1]));
        }
        Ui.muted(f, getString(R.string.independent_notice));
        Ui.button(f, R.string.get_started, false, v ->
                repo().setSetting(AppConfig.KEY_ONBOARDED, "true", r -> {
                    startActivity(new Intent(this, LoginActivity.class));
                    finish();
                }));
    }
}
