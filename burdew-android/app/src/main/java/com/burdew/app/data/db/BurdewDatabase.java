package com.burdew.app.data.db;

import android.content.Context;

import androidx.room.Database;
import androidx.room.Room;
import androidx.room.RoomDatabase;

import com.burdew.app.config.AppConfig;
import com.burdew.app.data.db.Daos.*;
import com.burdew.app.data.db.Entities.*;

@Database(
        entities = {Customer.class, User.class, ServiceRequest.class, Transaction.class, Receipt.class,
                SmsMessage.class, LocationRecord.class, AuditLog.class, SyncItem.class, Setting.class},
        version = 1,
        exportSchema = true)
public abstract class BurdewDatabase extends RoomDatabase {

    public abstract CustomerDao customers();
    public abstract UserDao users();
    public abstract RequestDao requests();
    public abstract TransactionDao transactions();
    public abstract ReceiptDao receipts();
    public abstract SmsDao sms();
    public abstract LocationDao locations();
    public abstract AuditDao audit();
    public abstract SyncDao sync();
    public abstract SettingDao settings();

    private static volatile BurdewDatabase instance;

    public static BurdewDatabase get(Context context) {
        if (instance == null) {
            synchronized (BurdewDatabase.class) {
                if (instance == null) {
                    instance = Room.databaseBuilder(context.getApplicationContext(),
                                    BurdewDatabase.class, AppConfig.DATABASE_NAME)
                            // Add real Migration objects here for every schema change (see schemas/).
                            // Never use fallbackToDestructiveMigration: it would wipe transaction records.
                            .build();
                }
            }
        }
        return instance;
    }

    /** Closes the singleton so a backup file can be restored over it. */
    public static synchronized void closeInstance() {
        if (instance != null) {
            instance.close();
            instance = null;
        }
    }
}
