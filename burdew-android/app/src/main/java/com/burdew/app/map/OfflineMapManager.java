package com.burdew.app.map;

import android.content.Context;
import android.net.Uri;

import com.burdew.app.config.AppConfig;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;

/**
 * Offline map regions are Mapsforge ".map" files (vector OpenStreetMap data) kept in
 * files/maps/. One file per region ("asmara.map", "eritrea.map", "keren.map"...), so new
 * cities are added by importing another file — no code change, no internet.
 */
public final class OfflineMapManager {
    private OfflineMapManager() {}

    public static File dir(Context c) {
        File d = new File(c.getFilesDir(), AppConfig.MAPS_DIR);
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    public static List<File> regions(Context c) {
        List<File> out = new ArrayList<>();
        File[] files = dir(c).listFiles();
        if (files != null) for (File f : files) if (f.getName().endsWith(".map") && f.length() > 0) out.add(f);
        return out;
    }

    /** The region to open: the preferred one if present, else asmara, else any. Null when none installed. */
    public static File activeRegion(Context c, String preferred) {
        List<File> all = regions(c);
        if (all.isEmpty()) return null;
        for (String name : new String[]{preferred, AppConfig.DEFAULT_REGION}) {
            if (name == null) continue;
            for (File f : all) if (f.getName().equalsIgnoreCase(name + ".map")) return f;
        }
        return all.get(0);
    }

    /** Copies a picked file (USB, Bluetooth, SD card…) into app storage. Validates the Mapsforge header. */
    public static File importRegion(Context c, Uri source, String regionName) throws IOException {
        String safe = regionName.trim().toLowerCase(Locale.ROOT).replaceAll("[^a-z0-9_-]", "_");
        if (safe.isEmpty()) safe = AppConfig.DEFAULT_REGION;
        File tmp = new File(dir(c), safe + ".map.part");
        try (InputStream in = c.getContentResolver().openInputStream(source);
             OutputStream out = new FileOutputStream(tmp)) {
            if (in == null) throw new IOException("Cannot open file");
            byte[] buf = new byte[64 * 1024];
            int n;
            boolean first = true;
            while ((n = in.read(buf)) > 0) {
                if (first) {
                    // Mapsforge files start with the magic string "mapsforge binary OSM"
                    String magic = new String(buf, 0, Math.min(n, 20), "US-ASCII");
                    if (!magic.startsWith("mapsforge binary OSM")) throw new IOException("Not a Mapsforge .map file");
                    first = false;
                }
                out.write(buf, 0, n);
            }
        } catch (IOException e) {
            //noinspection ResultOfMethodCallIgnored
            tmp.delete();
            throw e;
        }
        File target = new File(dir(c), safe + ".map");
        if (target.exists() && !target.delete()) throw new IOException("Cannot replace existing map");
        if (!tmp.renameTo(target)) throw new IOException("Cannot save map");
        return target;
    }
}
