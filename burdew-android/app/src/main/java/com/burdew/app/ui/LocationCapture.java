package com.burdew.app.ui;

import android.app.Activity;
import android.content.Intent;
import android.location.Location;
import android.provider.Settings;
import android.widget.Button;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.appcompat.app.AlertDialog;

import com.burdew.app.R;
import com.burdew.app.location.LocationHelper;
import com.burdew.app.ui.map.MapActivity;

import java.util.Locale;

/** Reusable "location" block for customer and request forms: GPS fix, pick on map, or clear. */
public class LocationCapture {

    public static final int PICK_ON_MAP = 5101;

    public Double lat, lon;
    public Float accuracy;
    public String source;          // GPS, MAP_PICK, or null when unchanged
    private final Activity a;
    private final TextView label;
    private final Button gpsButton;
    private final LocationHelper helper;

    public LocationCapture(Activity a, LinearLayout parent) {
        this.a = a;
        this.helper = new LocationHelper(a);
        Ui.section(parent, R.string.location);
        label = Ui.muted(parent, "");
        gpsButton = Ui.button(parent, R.string.use_gps, true, v -> capture());
        Ui.button(parent, R.string.pick_on_map, true, v -> {
            Intent i = new Intent(a, MapActivity.class).putExtra(MapActivity.EXTRA_PICK, true);
            if (lat != null) i.putExtra(MapActivity.EXTRA_LAT, lat).putExtra(MapActivity.EXTRA_LON, lon);
            a.startActivityForResult(i, PICK_ON_MAP);
        });
        render();
    }

    public void set(Double lat, Double lon) {
        this.lat = lat;
        this.lon = lon;
        render();
    }

    private void render() {
        if (lat == null) {
            label.setText(R.string.no_location);
        } else {
            String acc = accuracy == null ? "" : String.format(Locale.ROOT, " (±%.0f m)", accuracy);
            label.setText(String.format(Locale.ROOT, "%.6f, %.6f", lat, lon) + acc);
        }
    }

    private void capture() {
        if (!LocationHelper.hasPermission(a)) {
            LocationHelper.requestPermission(a);
            return;
        }
        gpsButton.setEnabled(false);
        label.setText(R.string.waiting_for_gps);
        helper.requestSingleFix(new LocationHelper.Listener() {
            @Override public void onFix(Location l) {
                gpsButton.setEnabled(true);
                lat = l.getLatitude();
                lon = l.getLongitude();
                accuracy = l.hasAccuracy() ? l.getAccuracy() : null;
                source = "GPS";
                render();
            }

            @Override public void onFailure(String reason, Location bestEffort) {
                gpsButton.setEnabled(true);
                render();
                if ("GPS_DISABLED".equals(reason)) {
                    new AlertDialog.Builder(a).setMessage(R.string.gps_disabled)
                            .setPositiveButton(R.string.open_settings, (d, w) ->
                                    a.startActivity(new Intent(Settings.ACTION_LOCATION_SOURCE_SETTINGS)))
                            .setNegativeButton(android.R.string.cancel, null).show();
                } else if ("PERMISSION_DENIED".equals(reason)) {
                    Ui.toast(a, a.getString(R.string.location_permission_denied));
                } else {
                    Ui.toast(a, a.getString(R.string.gps_timeout));
                }
            }
        });
    }

    /** Call from Activity.onActivityResult. Returns true if handled. */
    public boolean onActivityResult(int requestCode, int resultCode, Intent data) {
        if (requestCode != PICK_ON_MAP) return false;
        if (resultCode == Activity.RESULT_OK && data != null) {
            lat = data.getDoubleExtra(MapActivity.EXTRA_LAT, 0);
            lon = data.getDoubleExtra(MapActivity.EXTRA_LON, 0);
            accuracy = null;
            source = "MAP_PICK";
            render();
        }
        return true;
    }

    /** Call from onRequestPermissionsResult. */
    public void onPermissionResult(int requestCode) {
        if (requestCode == LocationHelper.PERMISSION_REQUEST && LocationHelper.hasPermission(a)) capture();
        else if (requestCode == LocationHelper.PERMISSION_REQUEST)
            Ui.toast(a, a.getString(R.string.location_permission_denied));
    }

    public void stop() { helper.cancel(); }
}
