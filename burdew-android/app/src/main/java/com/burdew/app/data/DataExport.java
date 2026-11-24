package com.burdew.app.data;

import android.content.Context;
import android.database.Cursor;
import android.database.sqlite.SQLiteDatabase;
import android.net.Uri;

import com.burdew.app.config.AppConfig;
import com.burdew.app.core.Money;
import com.burdew.app.data.db.BurdewDatabase;
import com.burdew.app.data.db.Daos.TxRow;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;
import java.nio.charset.Charset;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.List;
import java.util.Locale;

/**
 * Offline backup and transfer (spec Part 17). Files are written to the app cache and
 * shared through the Android share sheet (Bluetooth, Nearby Share, USB file manager).
 * Must run off the main thread.
 */
public final class DataExport {
    private DataExport() {}

    /** Keep equal to @Database(version) in BurdewDatabase. */
    private static final int SCHEMA_VERSION = 1;

    private static File exportDir(Context c) {
        File d = new File(c.getCacheDir(), "exports");
        //noinspection ResultOfMethodCallIgnored
        d.mkdirs();
        return d;
    }

    private static String stamp() {
        return new SimpleDateFormat("yyyyMMdd-HHmm", Locale.ROOT).format(new Date());
    }

    /** CSV of transactions for spreadsheets/accountants. Amounts in NFA. */
    public static File transactionsCsv(Context c, List<TxRow> rows) throws IOException {
        File f = new File(exportDir(c), "burdew-transactions-" + stamp() + ".csv");
        try (Writer w = new OutputStreamWriter(new FileOutputStream(f), Charset.forName("UTF-8"))) {
            w.write('﻿'); // BOM so Excel reads Tigrinya/Arabic names correctly
            w.write("date,request,customer,provider,service,bill_nfa,collected_nfa,paid_to_utility_nfa,"
                    + "service_charge_nfa,provider_fee_nfa,payment_status,request_status,receipt_ref,voided\n");
            SimpleDateFormat df = new SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.ROOT);
            for (TxRow t : rows) {
                w.write(csv(df.format(new Date(t.transactionDate))) + "," + csv(t.requestCode) + "," + csv(t.customerName) + ","
                        + csv(t.providerName) + "," + t.serviceType + "," + Money.plain(t.billAmount) + ","
                        + Money.plain(t.amountCollected) + "," + Money.plain(t.amountPaid) + "," + Money.plain(t.serviceCharge) + ","
                        + Money.plain(t.providerFee) + "," + t.paymentStatus + "," + t.requestStatus + ","
                        + csv(t.receiptReference) + "," + t.voided + "\n");
            }
        }
        return f;
    }

    private static String csv(String s) {
        if (s == null) return "";
        // Prevent spreadsheet formula injection from customer-supplied text.
        if (!s.isEmpty() && "=+-@".indexOf(s.charAt(0)) >= 0) s = "'" + s;
        return "\"" + s.replace("\"", "\"\"") + "\"";
    }

    /**
     * Full database backup: a consistent copy of burdew.db. Receipt photos are not included
     * (they can be large); their paths and references are.
     */
    public static File databaseBackup(Context c, BurdewDatabase db) throws IOException {
        // Flush the write-ahead log into the main file so the copy is complete.
        try (Cursor cur = db.getOpenHelper().getWritableDatabase().query("PRAGMA wal_checkpoint(FULL)")) {
            cur.moveToFirst();
        }
        File src = c.getDatabasePath(AppConfig.DATABASE_NAME);
        File out = new File(exportDir(c), "burdew-backup-" + stamp() + ".db");
        copy(new FileInputStream(src), new FileOutputStream(out));
        return out;
    }

    /**
     * Restores a backup. The current database is copied to files/backups/ first, the
     * incoming file is checked to be a BURDEW SQLite database, and only then swapped in.
     * Returns the path of the safety copy.
     */
    public static File restore(Context c, Uri source) throws IOException {
        File incoming = new File(exportDir(c), "restore-incoming.db");
        InputStream in = c.getContentResolver().openInputStream(source);
        if (in == null) throw new IOException("Cannot open file");
        copy(in, new FileOutputStream(incoming));
        validate(incoming);

        File current = c.getDatabasePath(AppConfig.DATABASE_NAME);
        File backups = new File(c.getFilesDir(), "backups");
        //noinspection ResultOfMethodCallIgnored
        backups.mkdirs();
        File safety = new File(backups, "before-restore-" + stamp() + ".db");

        BurdewDatabase.get(c).getOpenHelper().getWritableDatabase().query("PRAGMA wal_checkpoint(FULL)").close();
        BurdewDatabase.closeInstance();
        copy(new FileInputStream(current), new FileOutputStream(safety));
        //noinspection ResultOfMethodCallIgnored
        new File(current.getPath() + "-wal").delete();
        //noinspection ResultOfMethodCallIgnored
        new File(current.getPath() + "-shm").delete();
        copy(new FileInputStream(incoming), new FileOutputStream(current));
        //noinspection ResultOfMethodCallIgnored
        incoming.delete();
        return safety;
    }

    private static void validate(File f) throws IOException {
        SQLiteDatabase d = null;
        try {
            d = SQLiteDatabase.openDatabase(f.getPath(), null, SQLiteDatabase.OPEN_READONLY);
            try (Cursor cur = d.rawQuery("SELECT COUNT(*) FROM sqlite_master WHERE type='table' AND name IN "
                    + "('customers','users','service_requests','transactions')", null)) {
                if (!cur.moveToFirst() || cur.getInt(0) != 4) throw new IOException("Not a BURDEW backup");
            }
            try (Cursor cur = d.rawQuery("PRAGMA integrity_check", null)) {
                if (!cur.moveToFirst() || !"ok".equalsIgnoreCase(cur.getString(0))) throw new IOException("Backup file is damaged");
            }
            // A backup from a newer app version cannot be opened by this one (Room would refuse to downgrade).
            if (d.getVersion() > SCHEMA_VERSION) throw new IOException("Backup is from a newer BURDEW version; update the app first");
        } catch (android.database.sqlite.SQLiteException e) {
            throw new IOException("Not a valid database file");
        } finally {
            if (d != null) d.close();
        }
    }

    private static void copy(InputStream in, OutputStream out) throws IOException {
        try (InputStream i = in; OutputStream o = out) {
            byte[] buf = new byte[64 * 1024];
            int n;
            while ((n = i.read(buf)) > 0) o.write(buf, 0, n);
        }
    }
}
