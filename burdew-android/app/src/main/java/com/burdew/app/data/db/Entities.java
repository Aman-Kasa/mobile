package com.burdew.app.data.db;

import androidx.annotation.NonNull;
import androidx.room.Entity;
import androidx.room.ForeignKey;
import androidx.room.Index;
import androidx.room.PrimaryKey;

import com.burdew.app.core.PaymentStatus;
import com.burdew.app.core.RequestStatus;
import com.burdew.app.core.Role;
import com.burdew.app.core.ServiceType;

/**
 * All Room entities (spec Part 13). IDs are UUID strings so records created offline
 * on different phones can later be merged by BURDEW HUB without collisions.
 * Money columns are long minor units (cents). Times are epoch milliseconds.
 */
public final class Entities {
    private Entities() {}

    @Entity(tableName = "customers",
            indices = {@Index("phoneNumber"), @Index("fullName")})
    public static class Customer {
        @PrimaryKey @NonNull public String customerId = "";
        @NonNull public String fullName = "";
        @NonNull public String phoneNumber = "";
        public String alternativePhone;
        public String address;
        public ServiceType defaultService;
        public String accountReference;
        public Double latitude;
        public Double longitude;
        public String notes;
        public boolean archived;          // soft delete: customer history is never destroyed
        public long createdAt;
        public long updatedAt;

        public boolean hasLocation() { return latitude != null && longitude != null; }
    }

    /** Operators of the app: administrators and service providers (spec "ServiceProviders"). */
    @Entity(tableName = "users",
            indices = {@Index(value = "username", unique = true)})
    public static class User {
        @PrimaryKey @NonNull public String userId = "";
        @NonNull public String fullName = "";
        public String phoneNumber;
        @NonNull public String username = "";
        @NonNull public String passwordHash = "";
        @NonNull public Role role = Role.PROVIDER;
        public boolean active = true;
        public int failedLogins;
        public long lockedUntil;
        public long createdAt;
        public long updatedAt;
    }

    @Entity(tableName = "service_requests",
            foreignKeys = {
                    @ForeignKey(entity = Customer.class, parentColumns = "customerId", childColumns = "customerId",
                            onDelete = ForeignKey.RESTRICT),
                    @ForeignKey(entity = User.class, parentColumns = "userId", childColumns = "assignedProviderId",
                            onDelete = ForeignKey.SET_NULL)},
            indices = {@Index("customerId"), @Index("assignedProviderId"), @Index("requestStatus"),
                    @Index(value = "requestCode", unique = true), @Index("createdAt")})
    public static class ServiceRequest {
        @PrimaryKey @NonNull public String requestId = "";
        @NonNull public String requestCode = "";     // short reference for SMS / phone, e.g. R-7KQ4M2
        @NonNull public String customerId = "";
        @NonNull public ServiceType serviceType = ServiceType.ELECTRICITY;
        public String accountReference;
        public String address;
        public String description;
        public Double latitude;
        public Double longitude;
        public String assignedProviderId;
        @NonNull public RequestStatus requestStatus = RequestStatus.NEW;
        public int priority;                         // 0 normal, 1 high, 2 urgent
        @NonNull public String source = "MANUAL";    // MANUAL or SMS
        public long createdAt;
        public long updatedAt;
    }

    /** One transaction per request (unique index) — the database itself blocks duplicate payments. */
    @Entity(tableName = "transactions",
            foreignKeys = {
                    @ForeignKey(entity = ServiceRequest.class, parentColumns = "requestId", childColumns = "requestId",
                            onDelete = ForeignKey.RESTRICT)},
            indices = {@Index(value = "requestId", unique = true), @Index("providerId"), @Index("customerId"),
                    @Index("transactionDate"), @Index("paymentStatus")})
    public static class Transaction {
        @PrimaryKey @NonNull public String transactionId = "";
        @NonNull public String requestId = "";
        @NonNull public String customerId = "";
        public String providerId;
        @NonNull public ServiceType serviceType = ServiceType.ELECTRICITY;
        public long billAmount;          // the customer's utility bill — NOT BURDEW revenue
        public long amountCollected;     // cash received from the customer
        public long amountPaid;          // paid to the utility office
        public long serviceCharge;       // any extra charge to the customer
        public long providerFee;         // compensation snapshot, fixed when verified
        @NonNull public PaymentStatus paymentStatus = PaymentStatus.NOT_COLLECTED;
        public String receiptReference;
        public long transactionDate;
        public String notes;
        public boolean voided;
        public String voidReason;
        public long updatedAt;
    }

    @Entity(tableName = "receipts",
            foreignKeys = @ForeignKey(entity = Transaction.class, parentColumns = "transactionId",
                    childColumns = "transactionId", onDelete = ForeignKey.RESTRICT),
            indices = {@Index("transactionId"), @Index("receiptReference")})
    public static class Receipt {
        @PrimaryKey @NonNull public String receiptId = "";
        @NonNull public String transactionId = "";
        public String imagePath;          // file in app storage; images are never stored in SQLite
        public String receiptReference;
        public long capturedAt;
        public String notes;
    }

    @Entity(tableName = "sms_messages",
            indices = {@Index("fingerprint"), @Index("processingStatus"), @Index("receivedAt")})
    public static class SmsMessage {
        @PrimaryKey @NonNull public String messageId = "";
        @NonNull public String phoneNumber = "";
        @NonNull public String messageBody = "";
        @NonNull public String messageType = "IN";         // IN or OUT
        @NonNull public String processingStatus = "RECEIVED";
        public String fingerprint;
        public String error;
        public long receivedAt;
        public String relatedRequestId;
    }

    @Entity(tableName = "location_records",
            indices = {@Index("customerId"), @Index("requestId")})
    public static class LocationRecord {
        @PrimaryKey @NonNull public String locationId = "";
        public String customerId;
        public String requestId;
        public double latitude;
        public double longitude;
        public Float accuracy;            // metres, when the GPS provides it
        public long capturedAt;
        @NonNull public String locationSource = "GPS";  // GPS, NETWORK, MAP_PICK
    }

    @Entity(tableName = "audit_logs",
            indices = {@Index({"entityType", "entityId"}), @Index("timestamp")})
    public static class AuditLog {
        @PrimaryKey @NonNull public String logId = "";
        public String userId;
        @NonNull public String entityType = "";
        @NonNull public String entityId = "";
        @NonNull public String action = "";
        public String previousValue;
        public String newValue;
        public long timestamp;
    }

    @Entity(tableName = "sync_queue",
            indices = {@Index("syncStatus"), @Index({"entityType", "entityId"})})
    public static class SyncItem {
        @PrimaryKey @NonNull public String syncId = "";
        @NonNull public String entityType = "";
        @NonNull public String entityId = "";
        @NonNull public String operationType = "";   // CREATE, UPDATE
        @NonNull public String syncStatus = "PENDING";
        public int attempts;
        public long createdAt;
        public long lastAttemptAt;
    }

    @Entity(tableName = "settings")
    public static class Setting {
        @PrimaryKey @NonNull public String key = "";
        public String value;
    }
}
