package com.burdew.app.location;

import android.Manifest;
import android.annotation.SuppressLint;
import android.app.Activity;
import android.content.Context;
import android.content.pm.PackageManager;
import android.location.Location;
import android.location.LocationListener;
import android.location.LocationManager;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;

import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

/**
 * One-shot GPS fix using the platform LocationManager (no Google Play Services).
 * Listens only until a fix arrives or the timeout passes — no continuous tracking.
 */
public class LocationHelper {

    public static final int PERMISSION_REQUEST = 4101;
    private static final long TIMEOUT_MS = 60_000;
    private static final float GOOD_ENOUGH_METRES = 25f;

    public interface Listener {
        void onFix(Location location);
        /** reason: PERMISSION_DENIED, GPS_DISABLED, TIMEOUT */
        void onFailure(String reason, Location bestEffort);
    }

    private final Context context;
    private final LocationManager lm;
    private final Handler handler = new Handler(Looper.getMainLooper());
    private LocationListener active;
    private Location best;

    public LocationHelper(Context context) {
        this.context = context.getApplicationContext();
        this.lm = (LocationManager) this.context.getSystemService(Context.LOCATION_SERVICE);
    }

    public static boolean hasPermission(Context c) {
        return ContextCompat.checkSelfPermission(c, Manifest.permission.ACCESS_FINE_LOCATION)
                == PackageManager.PERMISSION_GRANTED;
    }

    public static void requestPermission(Activity a) {
        ActivityCompat.requestPermissions(a, new String[]{Manifest.permission.ACCESS_FINE_LOCATION,
                Manifest.permission.ACCESS_COARSE_LOCATION}, PERMISSION_REQUEST);
    }

    public boolean gpsEnabled() {
        return lm != null && lm.isProviderEnabled(LocationManager.GPS_PROVIDER);
    }

    @SuppressLint("MissingPermission") // checked at the top
    public void requestSingleFix(Listener listener) {
        cancel();
        if (!hasPermission(context)) { listener.onFailure("PERMISSION_DENIED", null); return; }
        if (!gpsEnabled()) { listener.onFailure("GPS_DISABLED", lastKnown()); return; }
        best = null;
        active = new LocationListener() {
            @Override public void onLocationChanged(Location l) {
                if (best == null || l.getAccuracy() < best.getAccuracy()) best = l;
                if (l.hasAccuracy() && l.getAccuracy() <= GOOD_ENOUGH_METRES) {
                    cancel();
                    listener.onFix(l);
                }
            }
            @Override public void onStatusChanged(String p, int s, Bundle b) {}
            @Override public void onProviderEnabled(String p) {}
            @Override public void onProviderDisabled(String p) {
                cancel();
                listener.onFailure("GPS_DISABLED", best);
            }
        };
        lm.requestLocationUpdates(LocationManager.GPS_PROVIDER, 1000, 0, active, Looper.getMainLooper());
        handler.postDelayed(() -> {
            if (active == null) return;
            cancel();
            if (best != null) listener.onFix(best); // weaker fix, accuracy shown to the user
            else listener.onFailure("TIMEOUT", lastKnown());
        }, TIMEOUT_MS);
    }

    @SuppressLint("MissingPermission")
    private Location lastKnown() {
        if (!hasPermission(context) || lm == null) return null;
        Location gps = lm.getLastKnownLocation(LocationManager.GPS_PROVIDER);
        Location net = lm.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
                ? lm.getLastKnownLocation(LocationManager.NETWORK_PROVIDER) : null;
        if (gps == null) return net;
        if (net == null) return gps;
        return gps.getTime() >= net.getTime() ? gps : net;
    }

    public void cancel() {
        handler.removeCallbacksAndMessages(null);
        if (active != null && lm != null) lm.removeUpdates(active);
        active = null;
    }
}
