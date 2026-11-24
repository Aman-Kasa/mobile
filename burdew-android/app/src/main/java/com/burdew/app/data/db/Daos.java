package com.burdew.app.data.db;

import androidx.lifecycle.LiveData;
import androidx.room.Dao;
import androidx.room.Insert;
import androidx.room.OnConflictStrategy;
import androidx.room.Query;
import androidx.room.Update;

import com.burdew.app.data.db.Entities.*;

import java.util.List;

public final class Daos {
    private Daos() {}

    @Dao
    public interface CustomerDao {
        @Insert void insert(Customer c);
        @Update void update(Customer c);

        @Query("SELECT * FROM customers WHERE customerId = :id")
        Customer get(String id);

        @Query("SELECT * FROM customers WHERE customerId = :id")
        LiveData<Customer> observe(String id);

        @Query("SELECT * FROM customers WHERE archived = 0 AND (:q = '' OR fullName LIKE '%' || :q || '%' "
                + "OR phoneNumber LIKE '%' || :q || '%' OR accountReference LIKE '%' || :q || '%' "
                + "OR address LIKE '%' || :q || '%') ORDER BY fullName COLLATE NOCASE")
        LiveData<List<Customer>> search(String q);

        @Query("SELECT * FROM customers WHERE archived = 0 ORDER BY fullName COLLATE NOCASE")
        List<Customer> allActive();

        @Query("SELECT * FROM customers WHERE archived = 0 AND latitude IS NOT NULL AND longitude IS NOT NULL")
        List<Customer> withLocation();

        @Query("SELECT * FROM customers WHERE phoneNumber = :phone AND archived = 0 LIMIT 1")
        Customer findByPhone(String phone);

        @Query("SELECT COUNT(*) FROM customers WHERE archived = 0")
        LiveData<Integer> count();

        @Query("SELECT * FROM customers") List<Customer> dumpAll();
    }

    @Dao
    public interface UserDao {
        @Insert void insert(User u);
        @Update void update(User u);

        @Query("SELECT * FROM users WHERE userId = :id") User get(String id);
        @Query("SELECT * FROM users WHERE username = :username COLLATE NOCASE LIMIT 1") User findByUsername(String username);
        @Query("SELECT COUNT(*) FROM users") int countSync();

        @Query("SELECT * FROM users WHERE role = 'PROVIDER' ORDER BY active DESC, fullName COLLATE NOCASE")
        LiveData<List<User>> providers();

        @Query("SELECT * FROM users WHERE role = 'PROVIDER' AND active = 1 ORDER BY fullName COLLATE NOCASE")
        List<User> activeProvidersSync();

        @Query("SELECT * FROM users") List<User> dumpAll();
    }

    /** Request joined with the names the list screens need. */
    public static class RequestRow {
        public String requestId;
        public String requestCode;
        public String customerName;
        public String customerPhone;
        public String providerName;
        public String serviceType;
        public String requestStatus;
        public String address;
        public int priority;
        public long createdAt;
    }

    @Dao
    public interface RequestDao {
        @Insert void insert(ServiceRequest r);
        @Update void update(ServiceRequest r);

        @Query("SELECT * FROM service_requests WHERE requestId = :id") ServiceRequest get(String id);
        @Query("SELECT * FROM service_requests WHERE requestId = :id") LiveData<ServiceRequest> observe(String id);
        @Query("SELECT COUNT(*) FROM service_requests WHERE requestCode = :code") int codeExists(String code);

        String ROW = "SELECT r.requestId, r.requestCode, c.fullName AS customerName, c.phoneNumber AS customerPhone, "
                + "u.fullName AS providerName, r.serviceType, r.requestStatus, r.address, r.priority, r.createdAt "
                + "FROM service_requests r JOIN customers c ON c.customerId = r.customerId "
                + "LEFT JOIN users u ON u.userId = r.assignedProviderId ";

        @Query(ROW + "WHERE (:status IS NULL OR r.requestStatus = :status) "
                + "AND (:providerId IS NULL OR r.assignedProviderId = :providerId) "
                + "ORDER BY r.priority DESC, r.createdAt DESC")
        LiveData<List<RequestRow>> list(String status, String providerId);

        @Query(ROW + "WHERE r.customerId = :customerId ORDER BY r.createdAt DESC")
        LiveData<List<RequestRow>> forCustomer(String customerId);

        @Query(ROW + "ORDER BY r.updatedAt DESC LIMIT 8")
        LiveData<List<RequestRow>> recent();

        @Query("SELECT COUNT(*) FROM service_requests WHERE requestStatus = :status "
                + "AND (:providerId IS NULL OR assignedProviderId = :providerId)")
        LiveData<Integer> countByStatus(String status, String providerId);

        @Query("SELECT COUNT(*) FROM service_requests WHERE requestStatus NOT IN ('RECEIPT_DELIVERED','CANCELLED') "
                + "AND createdAt < :before")
        int outstandingBefore(long before);

        /** Open request for the same account+service: used to stop duplicate SMS / manual requests. */
        @Query("SELECT * FROM service_requests WHERE customerId = :customerId AND serviceType = :service "
                + "AND accountReference = :account AND requestStatus NOT IN ('RECEIPT_DELIVERED','CANCELLED','FAILED') LIMIT 1")
        ServiceRequest findOpenDuplicate(String customerId, String service, String account);

        @Query("SELECT * FROM service_requests") List<ServiceRequest> dumpAll();
    }

    public static class TxRow {
        public String transactionId;
        public String requestId;
        public String requestCode;
        public String customerName;
        public String providerName;
        public String serviceType;
        public long billAmount;
        public long amountCollected;
        public long amountPaid;
        public long serviceCharge;
        public long providerFee;
        public String paymentStatus;
        public String requestStatus;
        public String receiptReference;
        public boolean voided;
        public long transactionDate;
    }

    @Dao
    public interface TransactionDao {
        @Insert(onConflict = OnConflictStrategy.ABORT) void insert(Transaction t);
        @Update void update(Transaction t);

        @Query("SELECT * FROM transactions WHERE requestId = :requestId") Transaction forRequest(String requestId);
        @Query("SELECT * FROM transactions WHERE requestId = :requestId") LiveData<Transaction> observeForRequest(String requestId);

        String ROW = "SELECT t.transactionId, t.requestId, r.requestCode, c.fullName AS customerName, "
                + "u.fullName AS providerName, t.serviceType, t.billAmount, t.amountCollected, t.amountPaid, "
                + "t.serviceCharge, t.providerFee, t.paymentStatus, r.requestStatus, t.receiptReference, t.voided, "
                + "t.transactionDate FROM transactions t JOIN service_requests r ON r.requestId = t.requestId "
                + "JOIN customers c ON c.customerId = t.customerId LEFT JOIN users u ON u.userId = t.providerId ";

        @Query(ROW + "WHERE t.transactionDate BETWEEN :from AND :to "
                + "AND (:providerId IS NULL OR t.providerId = :providerId) ORDER BY t.transactionDate DESC")
        List<TxRow> between(long from, long to, String providerId);

        @Query(ROW + "WHERE t.customerId = :customerId ORDER BY t.transactionDate DESC")
        LiveData<List<TxRow>> forCustomer(String customerId);

        /** Earnings = sum of fee snapshots on verified, delivered, non-voided work. */
        @Query("SELECT COALESCE(SUM(t.providerFee), 0) FROM transactions t JOIN service_requests r ON r.requestId = t.requestId "
                + "WHERE t.voided = 0 AND t.paymentStatus = 'VERIFIED' AND r.requestStatus = 'RECEIPT_DELIVERED' "
                + "AND (:providerId IS NULL OR t.providerId = :providerId)")
        LiveData<Long> earnings(String providerId);

        @Query("SELECT COUNT(*) FROM transactions t JOIN service_requests r ON r.requestId = t.requestId "
                + "WHERE t.voided = 0 AND t.paymentStatus = 'VERIFIED' AND r.requestStatus = 'RECEIPT_DELIVERED' "
                + "AND (:providerId IS NULL OR t.providerId = :providerId)")
        LiveData<Integer> completedCount(String providerId);

        @Query("SELECT * FROM transactions") List<Transaction> dumpAll();
    }

    @Dao
    public interface ReceiptDao {
        @Insert void insert(Receipt r);
        @Query("SELECT * FROM receipts WHERE transactionId = :txId ORDER BY capturedAt DESC") LiveData<List<Receipt>> forTransaction(String txId);
        @Query("SELECT COUNT(*) FROM receipts WHERE transactionId = :txId AND imagePath IS NOT NULL") int photoCount(String txId);
        @Query("SELECT * FROM receipts") List<Receipt> dumpAll();
    }

    @Dao
    public interface SmsDao {
        @Insert void insert(SmsMessage m);
        @Update void update(SmsMessage m);
        @Query("SELECT * FROM sms_messages WHERE messageId = :id") SmsMessage get(String id);

        @Query("SELECT COUNT(*) FROM sms_messages WHERE fingerprint = :fp AND messageType = 'IN' AND receivedAt > :since "
                + "AND processingStatus IN ('PROCESSED','RECEIVED')")
        int recentWithFingerprint(String fp, long since);

        @Query("SELECT * FROM sms_messages WHERE (:status IS NULL OR processingStatus = :status) ORDER BY receivedAt DESC LIMIT 300")
        LiveData<List<SmsMessage>> list(String status);

        @Query("SELECT COUNT(*) FROM sms_messages WHERE processingStatus = 'FAILED_PARSE'")
        LiveData<Integer> needsAttention();
    }

    @Dao
    public interface LocationDao {
        @Insert void insert(LocationRecord l);
    }

    @Dao
    public interface AuditDao {
        @Insert void insert(AuditLog l);
        @Query("SELECT * FROM audit_logs WHERE entityType = :type AND entityId = :id ORDER BY timestamp DESC")
        LiveData<List<AuditLog>> forEntity(String type, String id);
    }

    @Dao
    public interface SyncDao {
        @Insert void insert(SyncItem s);
        @Query("SELECT COUNT(*) FROM sync_queue WHERE syncStatus = 'PENDING'") LiveData<Integer> pendingCount();
    }

    @Dao
    public interface SettingDao {
        @Insert(onConflict = OnConflictStrategy.REPLACE) void put(Setting s);
        @Query("SELECT value FROM settings WHERE `key` = :key") String get(String key);
    }
}
