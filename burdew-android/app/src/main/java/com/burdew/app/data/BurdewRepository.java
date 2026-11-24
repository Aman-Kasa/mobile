package com.burdew.app.data;

import android.os.Handler;
import android.os.Looper;

import androidx.annotation.Nullable;

import com.burdew.app.config.AppConfig;
import com.burdew.app.core.Ids;
import com.burdew.app.core.PasswordHasher;
import com.burdew.app.core.PaymentStatus;
import com.burdew.app.core.PhoneNumbers;
import com.burdew.app.core.RequestStatus;
import com.burdew.app.core.Role;
import com.burdew.app.core.ServiceType;
import com.burdew.app.core.SmsRequestParser;
import com.burdew.app.core.TransactionRules;
import com.burdew.app.data.db.BurdewDatabase;
import com.burdew.app.data.db.Entities.*;

import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

/**
 * Service layer. Every write goes through here so that business rules, the audit log
 * and the sync queue are applied consistently, inside one database transaction.
 * Screens never write to DAOs directly.
 */
public class BurdewRepository {

    public interface Callback<T> { void done(Result<T> result); }

    public static final class Result<T> {
        public final T value;
        public final String error;
        private Result(T value, String error) { this.value = value; this.error = error; }
        public static <T> Result<T> ok(T v) { return new Result<>(v, null); }
        public static <T> Result<T> fail(String e) { return new Result<>(null, e); }
        public boolean ok() { return error == null; }
    }

    /** Thrown inside a DB transaction to roll it back with a user-facing message. */
    static final class RuleViolation extends RuntimeException {
        RuleViolation(String m) { super(m); }
    }

    private final BurdewDatabase db;
    private final Session session;
    private final ExecutorService io = Executors.newSingleThreadExecutor(); // serial writes = no races
    private final Handler main = new Handler(Looper.getMainLooper());

    public BurdewRepository(BurdewDatabase db, Session session) {
        this.db = db;
        this.session = session;
    }

    public BurdewDatabase db() { return db; }

    private interface Work<T> { T run(); }

    private <T> void async(Work<T> work, @Nullable Callback<T> cb) {
        io.execute(() -> {
            Result<T> r;
            try {
                r = Result.ok(db.runInTransaction((java.util.concurrent.Callable<T>) work::run));
            } catch (RuleViolation e) {
                r = Result.fail(e.getMessage());
            } catch (RuntimeException e) {
                r = Result.fail("Could not save: " + e.getMessage());
            }
            final Result<T> fr = r;
            if (cb != null) main.post(() -> cb.done(fr));
        });
    }

    private static void require(boolean cond, String message) {
        if (!cond) throw new RuleViolation(message);
    }

    private void requireAdmin() {
        require(session.isAdmin(), "Only an administrator can do this");
    }

    private void audit(String type, String id, String action, String before, String after) {
        long now = System.currentTimeMillis();
        AuditLog log = new AuditLog();
        log.logId = Ids.uuid();
        log.userId = session.userId();
        log.entityType = type;
        log.entityId = id;
        log.action = action;
        log.previousValue = before;
        log.newValue = after;
        log.timestamp = now;
        db.audit().insert(log);

        SyncItem s = new SyncItem();
        s.syncId = Ids.uuid();
        s.entityType = type;
        s.entityId = id;
        s.operationType = action.startsWith("CREATE") ? "CREATE" : "UPDATE";
        s.createdAt = now;
        db.sync().insert(s);
    }

    // ------------------------------------------------------------------ settings

    public long providerFee() {
        String v = db.settings().get(AppConfig.KEY_PROVIDER_FEE);
        try { return v == null ? AppConfig.DEFAULT_PROVIDER_FEE : Long.parseLong(v); }
        catch (NumberFormatException e) { return AppConfig.DEFAULT_PROVIDER_FEE; }
    }

    public boolean flag(String key, boolean def) {
        String v = db.settings().get(key);
        return v == null ? def : Boolean.parseBoolean(v);
    }

    public void setSetting(String key, String value, Callback<Void> cb) {
        async(() -> {
            if (AppConfig.KEY_PROVIDER_FEE.equals(key)) requireAdmin();
            String before = db.settings().get(key);
            Setting s = new Setting();
            s.key = key;
            s.value = value;
            db.settings().put(s);
            audit("Setting", key, "UPDATE", before, value);
            return null;
        }, cb);
    }

    /** Synchronous read for background callers (already off the main thread). */
    public String settingSync(String key) { return db.settings().get(key); }

    public void loadSettings(Callback<String[]> cb) {
        async(() -> new String[]{String.valueOf(providerFee()),
                String.valueOf(flag(AppConfig.KEY_SMS_AUTO_ACK, true)),
                String.valueOf(flag(AppConfig.KEY_SMS_AUTO_CREATE, true))}, cb);
    }

    // ------------------------------------------------------------------ auth

    public void hasUsers(Callback<Boolean> cb) {
        async(() -> db.users().countSync() > 0, cb);
    }

    public void createFirstAdmin(String fullName, String username, char[] password, Callback<User> cb) {
        async(() -> {
            require(db.users().countSync() == 0, "An administrator already exists");
            User u = newUser(fullName, null, username, password, Role.ADMIN);
            db.users().insert(u);
            session.signIn(u);
            audit("User", u.userId, "CREATE_ADMIN", null, u.username);
            return u;
        }, cb);
    }

    public void login(String username, char[] password, Callback<User> cb) {
        async(() -> {
            User u = db.users().findByUsername(username.trim());
            long now = System.currentTimeMillis();
            require(u != null, "Wrong username or password");
            require(u.active, "This account is deactivated");
            require(u.lockedUntil < now, "Too many attempts. Try again in a few minutes");
            if (!PasswordHasher.verify(password, u.passwordHash)) {
                u.failedLogins++;
                if (u.failedLogins >= AppConfig.MAX_FAILED_LOGINS) {
                    u.lockedUntil = now + AppConfig.LOCKOUT_MS;
                    u.failedLogins = 0;
                }
                db.users().update(u);
                return null; // committed so the counter sticks; reported as failure below
            }
            u.failedLogins = 0;
            u.lockedUntil = 0;
            db.users().update(u);
            session.signIn(u);
            return u;
        }, r -> {
            if (cb == null) return;
            if (r.ok() && r.value == null) cb.done(Result.fail("Wrong username or password"));
            else cb.done(r);
        });
    }

    private User newUser(String fullName, String phone, String username, char[] password, Role role) {
        require(fullName != null && !fullName.trim().isEmpty(), "Name is required");
        require(username != null && username.trim().matches("[A-Za-z0-9._-]{3,30}"),
                "Username: 3-30 letters, digits, dot, dash or underscore");
        require(password != null && password.length >= 8, "Password must be at least 8 characters");
        require(db.users().findByUsername(username.trim()) == null, "That username is taken");
        long now = System.currentTimeMillis();
        User u = new User();
        u.userId = Ids.uuid();
        u.fullName = fullName.trim();
        u.phoneNumber = PhoneNumbers.normalize(phone);
        u.username = username.trim();
        u.passwordHash = PasswordHasher.hash(password);
        u.role = role;
        u.createdAt = now;
        u.updatedAt = now;
        return u;
    }

    // ------------------------------------------------------------------ providers

    public void createProvider(String fullName, String phone, String username, char[] password, Callback<User> cb) {
        async(() -> {
            requireAdmin();
            User u = newUser(fullName, phone, username, password, Role.PROVIDER);
            db.users().insert(u);
            audit("User", u.userId, "CREATE_PROVIDER", null, u.username);
            return u;
        }, cb);
    }

    public void updateProvider(String userId, String fullName, String phone, boolean active,
                               @Nullable char[] newPassword, Callback<User> cb) {
        async(() -> {
            requireAdmin();
            User u = db.users().get(userId);
            require(u != null, "Provider not found");
            require(fullName != null && !fullName.trim().isEmpty(), "Name is required");
            String before = u.fullName + "|" + u.phoneNumber + "|active=" + u.active;
            u.fullName = fullName.trim();
            u.phoneNumber = PhoneNumbers.normalize(phone);
            u.active = active;
            if (newPassword != null && newPassword.length > 0) {
                require(newPassword.length >= 8, "Password must be at least 8 characters");
                u.passwordHash = PasswordHasher.hash(newPassword);
            }
            u.updatedAt = System.currentTimeMillis();
            db.users().update(u);
            audit("User", u.userId, "UPDATE_PROVIDER", before, u.fullName + "|" + u.phoneNumber + "|active=" + u.active);
            return u;
        }, cb);
    }

    public void getUser(String id, Callback<User> cb) { async(() -> db.users().get(id), cb); }

    public void activeProviders(Callback<List<User>> cb) { async(() -> db.users().activeProvidersSync(), cb); }

    // ------------------------------------------------------------------ customers

    public void getCustomer(String id, Callback<Customer> cb) { async(() -> db.customers().get(id), cb); }

    public void saveCustomer(Customer c, boolean isNew, Callback<Customer> cb) {
        async(() -> {
            require(c.fullName != null && !c.fullName.trim().isEmpty(), "Full name is required");
            String phone = PhoneNumbers.normalize(c.phoneNumber);
            require(PhoneNumbers.isPlausible(phone), "Enter a valid phone number");
            c.fullName = c.fullName.trim();
            c.phoneNumber = phone;
            c.alternativePhone = PhoneNumbers.normalize(c.alternativePhone);
            if (c.latitude != null) require(Math.abs(c.latitude) <= 90 && Math.abs(c.longitude) <= 180, "Invalid coordinates");
            long now = System.currentTimeMillis();
            c.updatedAt = now;
            if (isNew) {
                Customer existing = db.customers().findByPhone(phone);
                require(existing == null, "A customer with this phone already exists: " + (existing == null ? "" : existing.fullName));
                c.customerId = Ids.uuid();
                c.createdAt = now;
                db.customers().insert(c);
                audit("Customer", c.customerId, "CREATE", null, c.fullName);
            } else {
                Customer before = db.customers().get(c.customerId);
                require(before != null, "Customer not found");
                db.customers().update(c);
                audit("Customer", c.customerId, "UPDATE", before.fullName + "|" + before.phoneNumber + "|" + before.address,
                        c.fullName + "|" + c.phoneNumber + "|" + c.address);
            }
            return c;
        }, cb);
    }

    public void archiveCustomer(String customerId, Callback<Void> cb) {
        async(() -> {
            requireAdmin();
            Customer c = db.customers().get(customerId);
            require(c != null, "Customer not found");
            c.archived = true;
            c.updatedAt = System.currentTimeMillis();
            db.customers().update(c);
            audit("Customer", c.customerId, "ARCHIVE", "active", "archived");
            return null;
        }, cb);
    }

    public void recordLocation(@Nullable String customerId, @Nullable String requestId, double lat, double lon,
                               @Nullable Float accuracy, String source) {
        async(() -> {
            LocationRecord l = new LocationRecord();
            l.locationId = Ids.uuid();
            l.customerId = customerId;
            l.requestId = requestId;
            l.latitude = lat;
            l.longitude = lon;
            l.accuracy = accuracy;
            l.capturedAt = System.currentTimeMillis();
            l.locationSource = source;
            db.locations().insert(l);
            return null;
        }, null);
    }

    public void customersWithLocation(Callback<List<Customer>> cb) { async(() -> db.customers().withLocation(), cb); }

    // ------------------------------------------------------------------ requests

    public void getRequest(String id, Callback<ServiceRequest> cb) { async(() -> db.requests().get(id), cb); }

    public void createRequest(ServiceRequest r, Callback<ServiceRequest> cb) {
        async(() -> createRequestInTx(r), cb);
    }

    private ServiceRequest createRequestInTx(ServiceRequest r) {
        Customer c = db.customers().get(r.customerId);
        require(c != null, "Choose a customer");
        if (r.accountReference == null || r.accountReference.trim().isEmpty()) r.accountReference = c.accountReference;
        require(r.accountReference != null && !r.accountReference.trim().isEmpty(), "Account reference is required");
        r.accountReference = r.accountReference.trim();
        ServiceRequest dup = db.requests().findOpenDuplicate(r.customerId, r.serviceType.name(), r.accountReference);
        require(dup == null, "An open request already exists for this account: " + (dup == null ? "" : dup.requestCode));
        if (r.address == null || r.address.trim().isEmpty()) r.address = c.address;
        if (r.latitude == null && c.hasLocation()) { r.latitude = c.latitude; r.longitude = c.longitude; }
        long now = System.currentTimeMillis();
        r.requestId = Ids.uuid();
        String code;
        do { code = Ids.shortCode("R"); } while (db.requests().codeExists(code) > 0);
        r.requestCode = code;
        r.createdAt = now;
        r.updatedAt = now;
        if (r.assignedProviderId != null) {
            User p = db.users().get(r.assignedProviderId);
            require(p != null && p.active && p.role == Role.PROVIDER, "Choose an active provider");
            r.requestStatus = RequestStatus.ASSIGNED;
        } else {
            r.requestStatus = RequestStatus.NEW;
        }
        db.requests().insert(r);
        audit("ServiceRequest", r.requestId, "CREATE", null, r.requestStatus.name());
        return r;
    }

    public void assign(String requestId, String providerId, Callback<ServiceRequest> cb) {
        async(() -> {
            requireAdmin();
            ServiceRequest r = db.requests().get(requestId);
            require(r != null, "Request not found");
            require(r.requestStatus == RequestStatus.NEW || r.requestStatus == RequestStatus.PENDING
                    || r.requestStatus == RequestStatus.ASSIGNED, "This request can no longer be reassigned");
            User p = db.users().get(providerId);
            require(p != null && p.active && p.role == Role.PROVIDER, "Choose an active provider");
            String before = r.assignedProviderId + "|" + r.requestStatus;
            r.assignedProviderId = providerId;
            r.requestStatus = RequestStatus.ASSIGNED;
            r.updatedAt = System.currentTimeMillis();
            db.requests().update(r);
            audit("ServiceRequest", r.requestId, "ASSIGN", before, providerId + "|ASSIGNED");
            return r;
        }, cb);
    }

    public void changeStatus(String requestId, RequestStatus target, Callback<ServiceRequest> cb) {
        async(() -> {
            ServiceRequest r = db.requests().get(requestId);
            require(r != null, "Request not found");
            boolean mine = session.userId() != null && session.userId().equals(r.assignedProviderId);
            require(r.requestStatus.canMoveTo(target, session.role(), mine),
                    "Not allowed: " + r.requestStatus + " → " + target);
            Transaction t = db.transactions().forRequest(requestId);
            if (target == RequestStatus.ASSIGNED) require(r.assignedProviderId != null, "Assign a provider first");
            if (target == RequestStatus.PENDING) r.assignedProviderId = null;
            if (target == RequestStatus.PAYMENT_COMPLETED) {
                require(t != null && !t.voided && (t.paymentStatus == PaymentStatus.SUBMITTED
                        || t.paymentStatus == PaymentStatus.VERIFIED),
                        "Record the payment and mark it submitted to the utility first");
            }
            if (target == RequestStatus.RECEIPT_DELIVERED) {
                require(t != null && !t.voided && t.paymentStatus == PaymentStatus.VERIFIED,
                        "The payment must be verified with a receipt before delivery");
            }
            if (target == RequestStatus.CANCELLED && t != null && !t.voided
                    && t.paymentStatus != PaymentStatus.NOT_COLLECTED && t.paymentStatus != PaymentStatus.REFUNDED) {
                throw new RuleViolation("Money was collected. Record a refund on the transaction before cancelling");
            }
            RequestStatus before = r.requestStatus;
            r.requestStatus = target;
            r.updatedAt = System.currentTimeMillis();
            db.requests().update(r);
            audit("ServiceRequest", r.requestId, "STATUS", before.name(), target.name());
            return r;
        }, cb);
    }

    // ------------------------------------------------------------------ transactions

    public void getTransaction(String requestId, Callback<Transaction> cb) {
        async(() -> db.transactions().forRequest(requestId), cb);
    }

    /** Create or update the money record for a request. Verified records are locked. */
    public void saveTransaction(String requestId, long bill, long collected, long paid, long serviceCharge,
                                PaymentStatus status, String receiptRef, String notes, Callback<Transaction> cb) {
        async(() -> {
            ServiceRequest r = db.requests().get(requestId);
            require(r != null, "Request not found");
            boolean mine = session.userId() != null && session.userId().equals(r.assignedProviderId);
            require(session.isAdmin() || mine, "Only the assigned provider or an admin can record payments");
            require(r.requestStatus == RequestStatus.IN_PROGRESS || r.requestStatus == RequestStatus.PAYMENT_COMPLETED
                    || (session.isAdmin() && r.requestStatus == RequestStatus.CANCELLED && status == PaymentStatus.REFUNDED),
                    "Start the request (IN PROGRESS) before recording payments");
            require(status != PaymentStatus.VERIFIED, "Use Verify to mark a payment verified");
            require(bill >= 0 && collected >= 0 && paid >= 0 && serviceCharge >= 0, "Amounts cannot be negative");
            if (status == PaymentStatus.COLLECTED || status == PaymentStatus.SUBMITTED) require(collected > 0, "Enter the amount collected");
            if (status == PaymentStatus.SUBMITTED) {
                require(paid > 0, "Enter the amount paid to the utility");
                require(paid <= collected, "Amount paid cannot exceed amount collected");
            }
            Transaction t = db.transactions().forRequest(requestId);
            boolean isNew = t == null;
            if (isNew) {
                t = new Transaction();
                t.transactionId = Ids.uuid();
                t.requestId = requestId;
                t.customerId = r.customerId;
                t.serviceType = r.serviceType;
            } else {
                require(t.paymentStatus != PaymentStatus.VERIFIED || session.isAdmin(), "Verified payments are locked");
            }
            String before = isNew ? null : t.paymentStatus + "|" + t.amountCollected + "|" + t.amountPaid;
            t.providerId = r.assignedProviderId;
            t.billAmount = bill;
            t.amountCollected = collected;
            t.amountPaid = paid;
            t.serviceCharge = serviceCharge;
            // An admin correcting amounts on a verified payment must not silently un-verify it.
            boolean keepVerified = !isNew && t.paymentStatus == PaymentStatus.VERIFIED && status != PaymentStatus.REFUNDED;
            t.paymentStatus = keepVerified ? PaymentStatus.VERIFIED : status;
            t.receiptReference = receiptRef == null ? null : receiptRef.trim();
            t.notes = notes;
            t.transactionDate = System.currentTimeMillis();
            t.updatedAt = t.transactionDate;
            if (isNew) db.transactions().insert(t); else db.transactions().update(t);
            audit("Transaction", t.transactionId, isNew ? "CREATE" : "UPDATE", before,
                    t.paymentStatus + "|" + t.amountCollected + "|" + t.amountPaid);
            return t;
        }, cb);
    }

    /** Marks the payment VERIFIED after checking evidence, and snapshots the provider fee. */
    public void verifyPayment(String requestId, Callback<Transaction> cb) {
        async(() -> {
            Transaction t = db.transactions().forRequest(requestId);
            require(t != null, "No payment recorded yet");
            ServiceRequest r = db.requests().get(requestId);
            require(r != null, "Request not found");
            boolean mine = session.userId() != null && session.userId().equals(r.assignedProviderId);
            require(session.isAdmin() || mine, "Only the assigned provider or an admin can verify");
            require(t.paymentStatus == PaymentStatus.SUBMITTED, "Mark the payment as submitted to the utility first");
            boolean photo = db.receipts().photoCount(t.transactionId) > 0;
            List<String> problems = TransactionRules.verificationProblems(
                    t.billAmount, t.amountCollected, t.amountPaid, t.receiptReference, photo);
            require(problems.isEmpty(), problems.isEmpty() ? "" : problems.get(0));
            t.paymentStatus = PaymentStatus.VERIFIED;
            t.providerFee = providerFee();
            t.updatedAt = System.currentTimeMillis();
            db.transactions().update(t);
            audit("Transaction", t.transactionId, "VERIFY", "SUBMITTED", "VERIFIED fee=" + t.providerFee);
            return t;
        }, cb);
    }

    public void voidTransaction(String requestId, String reason, Callback<Transaction> cb) {
        async(() -> {
            requireAdmin();
            require(reason != null && !reason.trim().isEmpty(), "Give a reason");
            Transaction t = db.transactions().forRequest(requestId);
            require(t != null, "No transaction");
            t.voided = true;
            t.voidReason = reason.trim();
            t.updatedAt = System.currentTimeMillis();
            db.transactions().update(t);
            audit("Transaction", t.transactionId, "VOID", null, reason);
            return t;
        }, cb);
    }

    public void addReceipt(String transactionId, @Nullable String imagePath, @Nullable String reference,
                           Callback<Receipt> cb) {
        async(() -> {
            Receipt rc = new Receipt();
            rc.receiptId = Ids.uuid();
            rc.transactionId = transactionId;
            rc.imagePath = imagePath;
            rc.receiptReference = reference;
            rc.capturedAt = System.currentTimeMillis();
            db.receipts().insert(rc);
            audit("Receipt", rc.receiptId, "CREATE", null, imagePath);
            return rc;
        }, cb);
    }

    // ------------------------------------------------------------------ SMS

    /** Outcome of processing an incoming SMS, so the receiver knows what to reply. */
    public static final class SmsOutcome {
        public String status;           // IGNORED, DUPLICATE, FAILED_PARSE, PROCESSED, RECEIVED
        public String reply;            // null = send nothing
        public String requestCode;
    }

    /**
     * Synchronous: called from the SMS BroadcastReceiver's background thread.
     * Every incoming message is stored first, so nothing is lost if parsing fails.
     */
    public SmsOutcome handleIncomingSmsSync(String sender, String body) {
        return db.runInTransaction(() -> {
            SmsOutcome out = new SmsOutcome();
            long now = System.currentTimeMillis();
            SmsMessage m = new SmsMessage();
            m.messageId = Ids.uuid();
            m.phoneNumber = sender == null ? "" : sender;
            m.messageBody = body == null ? "" : body;
            m.messageType = "IN";
            m.receivedAt = now;
            m.fingerprint = SmsRequestParser.fingerprint(sender, body);

            if (!SmsRequestParser.looksLikeRequest(body)) {
                m.processingStatus = out.status = "IGNORED";
                db.sms().insert(m);
                return out;
            }
            if (db.sms().recentWithFingerprint(m.fingerprint, now - AppConfig.SMS_DUPLICATE_WINDOW_MS) > 0) {
                m.processingStatus = out.status = "DUPLICATE";
                db.sms().insert(m);
                return out; // no reply: the first copy was already acknowledged
            }
            SmsRequestParser.Result p = SmsRequestParser.parse(body, sender);
            boolean ack = flag(AppConfig.KEY_SMS_AUTO_ACK, true);
            if (!p.valid) {
                m.processingStatus = out.status = "FAILED_PARSE";
                m.error = android.text.TextUtils.join("; ", p.errors);
                db.sms().insert(m);
                out.reply = ack ? SmsRequestParser.rejection(p.errors) : null;
                return out;
            }
            if (!flag(AppConfig.KEY_SMS_AUTO_CREATE, true)) {
                m.processingStatus = out.status = "RECEIVED"; // operator processes it by hand
                db.sms().insert(m);
                return out;
            }
            try {
                Customer c = db.customers().findByPhone(p.phone);
                if (c == null) {
                    c = new Customer();
                    c.customerId = Ids.uuid();
                    c.fullName = p.name;
                    c.phoneNumber = p.phone;
                    c.address = p.address;
                    c.accountReference = p.account;
                    c.defaultService = p.service;
                    c.createdAt = c.updatedAt = now;
                    db.customers().insert(c);
                    audit("Customer", c.customerId, "CREATE_FROM_SMS", null, c.fullName);
                }
                ServiceRequest r = new ServiceRequest();
                r.customerId = c.customerId;
                r.serviceType = p.service;
                r.accountReference = p.account;
                r.address = p.address;
                r.description = p.note;
                r.source = "SMS";
                r = createRequestInTx(r);
                m.relatedRequestId = r.requestId;
                m.processingStatus = out.status = "PROCESSED";
                out.requestCode = r.requestCode;
                out.reply = ack ? SmsRequestParser.acknowledgement(r.requestCode, p.service) : null;
            } catch (RuleViolation e) {
                m.processingStatus = out.status = "FAILED_PARSE";
                m.error = e.getMessage();
            }
            db.sms().insert(m);
            return out;
        });
    }

    /** Operator fixed a failed SMS by hand and created the request: link them. */
    public void markSmsProcessed(String messageId, @Nullable String requestId, Callback<Void> cb) {
        async(() -> {
            SmsMessage m = db.sms().get(messageId);
            require(m != null, "Message not found");
            m.processingStatus = "PROCESSED";
            m.relatedRequestId = requestId;
            db.sms().update(m);
            audit("SmsMessage", messageId, "MANUAL_PROCESS", null, requestId);
            return null;
        }, cb);
    }

    public String recordOutgoingSmsSync(String phone, String body) {
        SmsMessage m = new SmsMessage();
        m.messageId = Ids.uuid();
        m.phoneNumber = phone;
        m.messageBody = body;
        m.messageType = "OUT";
        m.processingStatus = "PENDING";
        m.receivedAt = System.currentTimeMillis();
        db.sms().insert(m);
        return m.messageId;
    }

    public void updateOutgoingStatus(String messageId, String status, @Nullable String error) {
        async(() -> {
            SmsMessage m = db.sms().get(messageId);
            if (m != null) {
                // Never downgrade DELIVERED back to SENT if broadcasts arrive out of order.
                if (!"DELIVERED".equals(m.processingStatus) || !"SENT".equals(status)) {
                    m.processingStatus = status;
                    m.error = error;
                    db.sms().update(m);
                }
            }
            return null;
        }, null);
    }

    // ------------------------------------------------------------------ reports

    public void transactionsBetween(long from, long to, @Nullable String providerId,
                                    Callback<List<com.burdew.app.data.db.Daos.TxRow>> cb) {
        async(() -> db.transactions().between(from, to, providerId), cb);
    }

    public void outstandingBefore(long before, Callback<Integer> cb) {
        async(() -> db.requests().outstandingBefore(before), cb);
    }

    public void runInBackground(Runnable r) { io.execute(r); }

    public void postMain(Runnable r) { main.post(r); }

    public static String nullIfBlank(String s) {
        return s == null || s.trim().isEmpty() ? null : s.trim();
    }

    public static ServiceType serviceOrDefault(ServiceType s) { return s == null ? ServiceType.ELECTRICITY : s; }
}
