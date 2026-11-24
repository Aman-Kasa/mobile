package com.burdew.app.ui;

import android.content.Intent;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.ImageView;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AppCompatActivity;

import com.burdew.app.BurdewApp;
import com.burdew.app.R;
import com.burdew.app.config.AppConfig;

/** Shows the brand while the database opens, then routes to onboarding or login. */
public class SplashActivity extends AppCompatActivity {

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        LinearLayout root = new LinearLayout(this);
        root.setOrientation(LinearLayout.VERTICAL);
        root.setGravity(Gravity.CENTER);
        root.setBackgroundResource(R.color.burdew_primary);

        ImageView logo = new ImageView(this);
        logo.setImageResource(R.drawable.ic_burdew);
        root.addView(logo, new LinearLayout.LayoutParams(Ui.dp(this, 96), Ui.dp(this, 96)));

        TextView name = new TextView(this);
        name.setText(R.string.app_name);
        name.setTextColor(0xFFFFFFFF);
        name.setTextSize(30);
        name.setTypeface(name.getTypeface(), android.graphics.Typeface.BOLD);
        name.setGravity(Gravity.CENTER);
        root.addView(name);

        TextView tagline = new TextView(this);
        tagline.setText(R.string.tagline);
        tagline.setTextColor(getResources().getColor(R.color.burdew_accent));
        tagline.setGravity(Gravity.CENTER);
        root.addView(tagline);

        TextView status = new TextView(this);
        status.setText(R.string.initializing);
        status.setTextColor(0xB3FFFFFF);
        status.setPadding(0, Ui.dp(this, 32), 0, 0);
        status.setGravity(Gravity.CENTER);
        root.addView(status);
        setContentView(root);

        BurdewApp app = BurdewApp.of(this);
        // Touches the database on the worker thread, which opens/creates burdew.db.
        app.repo().runInBackground(() -> {
            boolean onboarded = Boolean.parseBoolean(app.repo().settingSync(AppConfig.KEY_ONBOARDED));
            app.repo().postMain(() -> {
                startActivity(new Intent(this, onboarded ? LoginActivity.class : OnboardingActivity.class));
                finish();
            });
        });
    }
}
