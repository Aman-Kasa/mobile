package com.burdew.app.ui.map;

import android.content.Intent;
import android.graphics.Color;
import android.graphics.drawable.Drawable;
import android.location.Location;
import android.os.Bundle;
import android.view.Gravity;
import android.widget.Button;
import android.widget.EditText;
import android.widget.FrameLayout;
import android.widget.LinearLayout;
import android.widget.TextView;

import androidx.annotation.NonNull;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.burdew.app.R;
import com.burdew.app.config.AppConfig;
import com.burdew.app.data.db.Entities.Customer;
import com.burdew.app.location.LocationHelper;
import com.burdew.app.map.OfflineMapManager;
import com.burdew.app.ui.BaseActivity;
import com.burdew.app.ui.Ui;
import com.burdew.app.ui.customers.CustomerEditActivity;

import org.mapsforge.core.graphics.Bitmap;
import org.mapsforge.core.model.LatLong;
import org.mapsforge.core.model.Point;
import org.mapsforge.map.android.graphics.AndroidGraphicFactory;
import org.mapsforge.map.android.util.AndroidUtil;
import org.mapsforge.map.android.view.MapView;
import org.mapsforge.map.layer.cache.TileCache;
import org.mapsforge.map.layer.overlay.Marker;
import org.mapsforge.map.layer.renderer.TileRendererLayer;
import org.mapsforge.map.reader.MapFile;
import org.mapsforge.map.rendertheme.internal.MapsforgeThemes;

import java.io.File;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Asmara offline map (spec screen 7). Renders a local Mapsforge file; never loads online tiles.
 * Modes: browse customers, or pick a location (EXTRA_PICK) and return it.
 */
public class MapActivity extends BaseActivity {

    public static final String EXTRA_PICK = "pick";
    public static final String EXTRA_LAT = "lat";
    public static final String EXTRA_LON = "lon";

    private MapView mapView;
    private TileCache tileCache;
    private Marker me;
    private final List<Customer> customers = new ArrayList<>();
    private LocationHelper gps;

    @Override
    protected void onReady(Bundle savedInstanceState) {
        boolean pick = getIntent().getBooleanExtra(EXTRA_PICK, false);
        setTitle(pick ? R.string.pick_on_map : R.string.offline_map);
        gps = new LocationHelper(this);

        File mapFile = OfflineMapManager.activeRegion(this, null);
        if (mapFile == null) {
            LinearLayout f = Ui.form(this);
            Ui.section(f, R.string.no_offline_map);
            Ui.text(f, getString(R.string.no_offline_map_body));
            Ui.button(f, R.string.settings, false, v ->
                    startActivity(new Intent(this, com.burdew.app.ui.settings.SettingsActivity.class)));
            return; // never fall back to an online map
        }

        FrameLayout root = new FrameLayout(this);
        mapView = new MapView(this);
        mapView.setClickable(true);
        mapView.getMapScaleBar().setVisible(true);
        mapView.setBuiltInZoomControls(true);
        root.addView(mapView);

        tileCache = AndroidUtil.createTileCache(this, "mapcache",
                mapView.getModel().displayModel.getTileSize(), 1f,
                mapView.getModel().frameBufferModel.getOverdrawFactor());
        TileRendererLayer tiles = new TileRendererLayer(tileCache, new MapFile(mapFile),
                mapView.getModel().mapViewPosition, AndroidGraphicFactory.INSTANCE);
        tiles.setXmlRenderTheme(MapsforgeThemes.DEFAULT);
        mapView.getLayerManager().getLayers().add(tiles);

        double lat = getIntent().getDoubleExtra(EXTRA_LAT, AppConfig.DEFAULT_LAT);
        double lon = getIntent().getDoubleExtra(EXTRA_LON, AppConfig.DEFAULT_LON);
        mapView.setCenter(new LatLong(lat, lon));
        mapView.setZoomLevel((byte) (getIntent().hasExtra(EXTRA_LAT) ? 17 : 14));

        // Status strip: offline indicator + region + OSM attribution (required by the ODbL licence)
        TextView strip = new TextView(this);
        strip.setText(getString(R.string.map_strip, mapFile.getName().replace(".map", "")));
        strip.setTextSize(11);
        strip.setTextColor(Color.WHITE);
        strip.setBackgroundColor(0xAA0D3B66);
        strip.setPadding(Ui.dp(this, 8), Ui.dp(this, 4), Ui.dp(this, 8), Ui.dp(this, 4));
        root.addView(strip, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.TOP | Gravity.START));

        LinearLayout bar = new LinearLayout(this);
        bar.setOrientation(LinearLayout.HORIZONTAL);
        bar.setPadding(Ui.dp(this, 8), 0, Ui.dp(this, 8), Ui.dp(this, 8));
        FrameLayout.LayoutParams barLp = new FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT,
                FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.BOTTOM);
        root.addView(bar, barLp);

        Button myLoc = barButton(bar, R.string.my_location);
        myLoc.setOnClickListener(v -> locateMe());

        if (pick) {
            // Crosshair: the map centre is the chosen point.
            TextView cross = new TextView(this);
            cross.setText("+");
            cross.setTextSize(36);
            cross.setTextColor(ContextCompat.getColor(this, R.color.burdew_error));
            root.addView(cross, new FrameLayout.LayoutParams(FrameLayout.LayoutParams.WRAP_CONTENT,
                    FrameLayout.LayoutParams.WRAP_CONTENT, Gravity.CENTER));
            Button use = barButton(bar, R.string.use_this_location);
            use.setOnClickListener(v -> {
                LatLong c = mapView.getModel().mapViewPosition.getCenter();
                setResult(RESULT_OK, new Intent().putExtra(EXTRA_LAT, c.latitude).putExtra(EXTRA_LON, c.longitude));
                finish();
            });
        } else {
            Button search = barButton(bar, R.string.search);
            search.setOnClickListener(v -> searchCustomers());
            loadCustomerMarkers();
        }
        setContentView(root);
    }

    private Button barButton(LinearLayout bar, int label) {
        Button b = new com.google.android.material.button.MaterialButton(this);
        b.setText(label);
        LinearLayout.LayoutParams lp = new LinearLayout.LayoutParams(0, LinearLayout.LayoutParams.WRAP_CONTENT, 1f);
        lp.setMargins(Ui.dp(this, 4), 0, Ui.dp(this, 4), 0);
        bar.addView(b, lp);
        return b;
    }

    private Bitmap pin(int colorRes) {
        Drawable d = ContextCompat.getDrawable(this, android.R.drawable.presence_online).mutate();
        d.setColorFilter(ContextCompat.getColor(this, colorRes), android.graphics.PorterDuff.Mode.SRC_IN);
        return AndroidGraphicFactory.convertToBitmap(d);
    }

    private void loadCustomerMarkers() {
        repo().customersWithLocation(r -> {
            if (failed(r) || mapView == null) return;
            customers.clear();
            customers.addAll(r.value);
            Bitmap icon = pin(R.color.burdew_secondary);
            for (Customer c : customers) {
                Marker m = new Marker(new LatLong(c.latitude, c.longitude), icon, 0, 0) {
                    @Override
                    public boolean onTap(LatLong tap, Point layerXY, Point tapXY) {
                        if (!contains(layerXY, tapXY)) return false;
                        showCustomer(c);
                        return true;
                    }
                };
                mapView.getLayerManager().getLayers().add(m);
            }
        });
    }

    private void showCustomer(Customer c) {
        new AlertDialog.Builder(this).setTitle(c.fullName)
                .setMessage(c.phoneNumber + "\n" + (c.address == null ? "" : c.address)
                        + String.format(Locale.ROOT, "\n%.6f, %.6f", c.latitude, c.longitude))
                .setPositiveButton(R.string.open, (d, w) -> startActivity(new Intent(this, CustomerEditActivity.class)
                        .putExtra(CustomerEditActivity.EXTRA_ID, c.customerId)))
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    /** Local search over saved customer locations (works fully offline). */
    private void searchCustomers() {
        EditText q = new EditText(this);
        q.setHint(R.string.search_customers);
        new AlertDialog.Builder(this).setTitle(R.string.search).setView(q)
                .setPositiveButton(R.string.search, (d, w) -> {
                    String s = Ui.val(q).toLowerCase(Locale.ROOT);
                    for (Customer c : customers) {
                        if (c.fullName.toLowerCase(Locale.ROOT).contains(s) || c.phoneNumber.contains(s)
                                || (c.address != null && c.address.toLowerCase(Locale.ROOT).contains(s))) {
                            mapView.setCenter(new LatLong(c.latitude, c.longitude));
                            mapView.setZoomLevel((byte) 17);
                            showCustomer(c);
                            return;
                        }
                    }
                    Ui.toast(this, getString(R.string.not_found));
                })
                .setNegativeButton(android.R.string.cancel, null).show();
    }

    private void locateMe() {
        if (!LocationHelper.hasPermission(this)) { LocationHelper.requestPermission(this); return; }
        Ui.toast(this, getString(R.string.waiting_for_gps));
        gps.requestSingleFix(new LocationHelper.Listener() {
            @Override public void onFix(Location l) { showMe(l); }
            @Override public void onFailure(String reason, Location best) {
                if (best != null) showMe(best);
                Ui.toast(MapActivity.this, getString("GPS_DISABLED".equals(reason) ? R.string.gps_disabled
                        : "PERMISSION_DENIED".equals(reason) ? R.string.location_permission_denied : R.string.gps_timeout));
            }
        });
    }

    private void showMe(Location l) {
        if (mapView == null) return;
        LatLong p = new LatLong(l.getLatitude(), l.getLongitude());
        if (me != null) mapView.getLayerManager().getLayers().remove(me);
        me = new Marker(p, pin(R.color.burdew_primary), 0, 0);
        mapView.getLayerManager().getLayers().add(me);
        mapView.setCenter(p);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] p, @NonNull int[] g) {
        super.onRequestPermissionsResult(requestCode, p, g);
        if (requestCode == LocationHelper.PERMISSION_REQUEST && LocationHelper.hasPermission(this)) locateMe();
    }

    @Override
    protected void onDestroy() {
        if (gps != null) gps.cancel();
        if (mapView != null) {
            mapView.destroyAll();
            mapView = null;
        }
        if (tileCache != null) tileCache.destroy();
        super.onDestroy();
    }
}
