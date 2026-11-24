package com.burdew.app.core;

/**
 * The money trail, kept separate from the request status (spec Part 14):
 * collected from customer -> submitted to the utility office -> verified by receipt evidence.
 */
public enum PaymentStatus {
    NOT_COLLECTED,
    COLLECTED,
    SUBMITTED,
    VERIFIED,
    REFUNDED
}
