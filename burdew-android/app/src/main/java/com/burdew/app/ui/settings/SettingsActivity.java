package com.burdew.app.ui.settings;

import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.burdew.app.BuildConfig;
import com.burdew.app.BurdewApp;
import com.burdew.app.R;
import com.burdew.app.config.AppConfig;
import com.burdew.app.core.Money;
import com.burdew.app.data.DataExport;
import com.burdew.app.map.OfflineMapManager;
import com.burdew.app.ui.BaseActivity;
import com.burdew.app.ui.SplashActivity;
import com.burdew.app.ui.Ui;
import com.burdew.app.ui.requests.ReceiptPhotos;

import java.io.File;
import java.util.List;
import java.util.Locale;

/** Settings (spec screen 15): fee, SMS behaviour, offline maps, backup / restore, about. */
public class SettingsActivity extends BaseActivity {

    private static final int PICK_MAP = 9101;
    private static final int PICK_BACKUP = 9102;
    private TextView mapsInfo;
    private EditText regionName;

    @Override
    protected void onReady(Bundle savedInstanceState) {
        setTitle(R.string.settings);
        boolean admin = session().isAdmin();
        LinearLayout f = Ui.form(this);

        if (admin) {
            Ui.section(f, R.string.compensation);
            EditText fee = Ui.field(f, R.string.provider_fee_nfa, Ui.MONEY);
            Ui.muted(f, getString(R.string.fee_hint));
            Ui.button(f, R.string.save, false, v -> {
                Long m = Money.parse(Ui.val(fee));
                if (m == null) { Ui.toast(this, getString(R.string.invalid_amount)); return; }
                repo().setSetting(AppConfig.KEY_PROVIDER_FEE, String.valueOf(m), r -> {
                    if (!failed(r)) Ui.toast(this, getString(R.string.saved));
                });
            });

            Ui.section(f, R.string.sms_settings);
            CheckBox autoCreate = new CheckBox(this);
            autoCreate.setText(R.string.sms_auto_create);
            f.addView(autoCreate);
            CheckBox autoAck = new CheckBox(this);
            autoAck.setText(R.string.sms_auto_ack);
            f.addView(autoAck);
            repo().loadSettings(r -> {
                if (!r.ok()) return;
                fee.setText(Money.plain(Long.parseLong(r.value[0])));
                autoAck.setChecked(Boolean.parseBoolean(r.value[1]));
                autoCreate.setChecked(Boolean.parseBoolean(r.value[2]));
                autoAck.setOnCheckedChangeListener((b, on) -> repo().setSetting(AppConfig.KEY_SMS_AUTO_ACK, String.valueOf(on), null));
                autoCreate.setOnCheckedChangeListener((b, on) -> repo().setSetting(AppConfig.KEY_SMS_AUTO_CREATE, String.valueOf(on), null));
            });
            Ui.muted(f, getString(R.string.sms_format_help));
        }

        Ui.section(f, R.string.offline_maps);
        mapsInfo = Ui.muted(f, "");
        renderMaps();
        if (admin) {
            regionName = Ui.field(f, R.string.region_name, Ui.PLAIN);
            regionName.setText(AppConfig.DEFAULT_REGION);
            Ui.button(f, R.string.import_map, true, v -> startActivityForResult(
                    new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), PICK_MAP));
            Ui.muted(f, getString(R.string.map_import_help));
        }

        if (admin) {
            Ui.section(f, R.string.backup);
            Ui.button(f, R.string.export_backup, false, v -> exportBackup());
            Ui.button(f, R.string.restore_backup, true, v -> new AlertDialog.Builder(this)
                    .setMessage(R.string.restore_warning)
                    .setPositiveButton(R.string.restore_backup, (d, w) -> startActivityForResult(
                            new Intent(Intent.ACTION_OPEN_DOCUMENT).addCategory(Intent.CATEGORY_OPENABLE).setType("*/*"), PICK_BACKUP))
                    .setNegativeButton(android.R.string.cancel, null).show());
            Ui.muted(f, getString(R.string.backup_help));
        }

        Ui.section(f, R.string.about);
        Ui.text(f, getString(R.string.app_name) + " " + BuildConfig.VERSION_NAME + " · " + getString(R.string.tagline));
        Ui.muted(f, getString(R.string.independent_notice));
        Ui.muted(f, getString(R.string.osm_attribution_full));
        Ui.muted(f, getString(R.string.language_note));
    }

    private void renderMaps() {
        List<File> regions = OfflineMapManager.regions(this);
        if (regions.isEmpty()) { mapsInfo.setText(R.string.no_offline_map); return; }
        StringBuilder sb = new StringBuilder();
        for (File r : regions) {
            sb.append(String.format(Locale.ROOT, "✓ %s (%.1f MB)\n", r.getName().replace(".map", ""), r.length() / 1048576.0));
        }
        mapsInfo.setText(sb.toString().trim());
    }

    private void exportBackup() {
        repo().runInBackground(() -> {
            try {
                File f = DataExport.databaseBackup(this, repo().db());
                Uri uri = ReceiptPhotos.uriFor(this, f);
                repo().postMain(() -> startActivity(Intent.createChooser(new Intent(Intent.ACTION_SEND)
                        .setType("application/octet-stream").putExtra(Intent.EXTRA_STREAM, uri)
                        .addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION), getString(R.string.export_backup))));
            } catch (Exception e) {
                repo().postMain(() -> Ui.toast(this, getString(R.string.export_failed, e.getMessage())));
            }
        });
    }

    @Override
    protected void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (resultCode != RESULT_OK || data == null || data.getData() == null) return;
        Uri uri = data.getData();
        if (requestCode == PICK_MAP) {
            String name = Ui.val(regionName);
            Ui.toast(this, getString(R.string.importing));
            repo().runInBackground(() -> {
                try {
                    OfflineMapManager.importRegion(this, uri, name);
                    repo().postMain(() -> { renderMaps(); Ui.toast(this, getString(R.string.map_imported)); });
                } catch (Exception e) {
                    repo().postMain(() -> Ui.toast(this, getString(R.string.import_failed, e.getMessage())));
                }
            });
        } else if (requestCode == PICK_BACKUP) {
            // Runs on the repository's serial executor, so no write can happen mid-swap.
            repo().runInBackground(() -> {
                try {
                    File safety = DataExport.restore(this, uri);
                    repo().postMain(() -> {
                        BurdewApp.of(this).reopenDatabase();
                        session().signOut();
                        Ui.toast(this, getString(R.string.restore_done, safety.getName()));
                        startActivity(new Intent(this, SplashActivity.class)
                                .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK | Intent.FLAG_ACTIVITY_CLEAR_TASK));
                    });
                } catch (Exception e) {
                    repo().postMain(() -> Ui.toast(this, getString(R.string.import_failed, e.getMessage())));
                }
            });
        }
    }
}
