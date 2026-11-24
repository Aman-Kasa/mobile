package com.burdew.app.core;

import java.util.Collection;

/**
 * Provider compensation. The fee is snapshotted onto each transaction when it is
 * verified, so changing the configured fee later never rewrites past earnings.
 */
public final class EarningsCalculator {
    private EarningsCalculator() {}

    public static final class Item {
        public final PaymentStatus payment;
        public final RequestStatus request;
        public final boolean voided;
        public final long providerFee;

        public Item(PaymentStatus payment, RequestStatus request, boolean voided, long providerFee) {
            this.payment = payment;
            this.request = request;
            this.voided = voided;
            this.providerFee = providerFee;
        }
    }

    public static long total(Collection<Item> items) {
        long sum = 0;
        for (Item i : items) {
            if (TransactionRules.earnsCompensation(i.payment, i.request, i.voided)) {
                sum = Math.addExact(sum, i.providerFee);
            }
        }
        return sum;
    }

    public static long forCount(int completed, long feePerTransaction) {
        return Math.multiplyExact((long) completed, feePerTransaction);
    }
}
