package com.burdew.app.core;

import java.util.ArrayList;
import java.util.List;

/**
 * Evidence rules: a payment is never marked complete without proof (spec Part 14),
 * and only verified, delivered work earns provider compensation (spec Part 4).
 */
public final class TransactionRules {
    private TransactionRules() {}

    /** Problems that block marking a payment VERIFIED. Empty list = OK. */
    public static List<String> verificationProblems(long billAmount, long amountCollected, long amountPaid,
                                                    String receiptReference, boolean hasReceiptPhoto) {
        List<String> problems = new ArrayList<>();
        if (billAmount <= 0) problems.add("Bill amount must be greater than zero");
        if (amountPaid <= 0) problems.add("Amount paid to the utility must be recorded");
        if (amountPaid > amountCollected) problems.add("Amount paid cannot exceed amount collected");
        boolean hasRef = receiptReference != null && !receiptReference.trim().isEmpty();
        if (!hasRef && !hasReceiptPhoto) problems.add("A receipt reference or receipt photo is required");
        return problems;
    }

    /** What the provider still owes the customer (change), or 0. */
    public static long balanceOwedToCustomer(long amountCollected, long amountPaid, long serviceCharge) {
        return Math.max(0, amountCollected - amountPaid - serviceCharge);
    }

    /** What the customer still owes, or 0. */
    public static long balanceOwedByCustomer(long amountCollected, long amountPaid, long serviceCharge) {
        return Math.max(0, amountPaid + serviceCharge - amountCollected);
    }

    /** Earning eligibility: verified payment on a request whose receipt reached the customer. */
    public static boolean earnsCompensation(PaymentStatus payment, RequestStatus request, boolean voided) {
        return !voided && payment == PaymentStatus.VERIFIED && request == RequestStatus.RECEIPT_DELIVERED;
    }
}
