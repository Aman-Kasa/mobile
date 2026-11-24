package com.burdew.app.data;

import com.burdew.app.core.Role;
import com.burdew.app.data.db.Entities.User;

/**
 * The signed-in operator, held in memory only. Closing the app (or the process being
 * killed) signs the user out, so a lost phone does not stay unlocked.
 */
public class Session {
    private volatile String userId;
    private volatile String fullName;
    private volatile Role role;

    public synchronized void signIn(User u) {
        userId = u.userId;
        fullName = u.fullName;
        role = u.role;
    }

    public synchronized void signOut() {
        userId = null;
        fullName = null;
        role = null;
    }

    public boolean isSignedIn() { return userId != null; }
    public boolean isAdmin() { return role == Role.ADMIN; }
    public String userId() { return userId; }
    public String fullName() { return fullName; }
    public Role role() { return role; }

    /** Providers only see their own work; admins see everything (null filter). */
    public String providerFilter() { return isAdmin() ? null : userId; }
}
