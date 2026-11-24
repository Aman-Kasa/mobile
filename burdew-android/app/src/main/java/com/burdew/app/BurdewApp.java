package com.burdew.app;

import android.content.Context;

import androidx.multidex.MultiDexApplication;

import com.burdew.app.data.BurdewRepository;
import com.burdew.app.data.Session;
import com.burdew.app.data.db.BurdewDatabase;

/** Simple service locator: lightweight dependency wiring without a DI framework. */
public class BurdewApp extends MultiDexApplication {

    private Session session;
    private BurdewRepository repository;

    @Override
    public void onCreate() {
        super.onCreate();
        // Mapsforge needs its Android graphics backend before any MapView is created.
        org.mapsforge.map.android.graphics.AndroidGraphicFactory.createInstance(this);
        session = new Session();
        repository = new BurdewRepository(BurdewDatabase.get(this), session);
    }

    public static BurdewApp of(Context c) { return (BurdewApp) c.getApplicationContext(); }

    public Session session() { return session; }

    public BurdewRepository repo() { return repository; }

    /** After a backup restore the database singleton is reopened. */
    public void reopenDatabase() {
        repository = new BurdewRepository(BurdewDatabase.get(this), session);
    }
}
