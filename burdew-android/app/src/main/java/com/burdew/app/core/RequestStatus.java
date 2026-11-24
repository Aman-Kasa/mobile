package com.burdew.app.core;

import java.util.Collections;
import java.util.EnumSet;
import java.util.Set;

/**
 * Lifecycle of a service request. Allowed transitions live here, in one place,
 * so the UI, repositories and a future BURDEW HUB server all enforce the same rules.
 */
public enum RequestStatus {
    NEW,
    PENDING,
    ASSIGNED,
    IN_PROGRESS,
    PAYMENT_COMPLETED,
    RECEIPT_DELIVERED,
    CANCELLED,
    FAILED;

    public Set<RequestStatus> next() {
        switch (this) {
            case NEW:               return EnumSet.of(PENDING, ASSIGNED, CANCELLED);
            case PENDING:           return EnumSet.of(ASSIGNED, CANCELLED);
            case ASSIGNED:          return EnumSet.of(IN_PROGRESS, PENDING, CANCELLED);
            case IN_PROGRESS:       return EnumSet.of(PAYMENT_COMPLETED, FAILED, CANCELLED);
            case PAYMENT_COMPLETED: return EnumSet.of(RECEIPT_DELIVERED);
            case FAILED:            return EnumSet.of(PENDING, CANCELLED);
            default:                return Collections.emptySet(); // RECEIPT_DELIVERED, CANCELLED are terminal
        }
    }

    public boolean isTerminal() {
        return next().isEmpty();
    }

    /** True while the request still needs work (used for "outstanding" reports). */
    public boolean isOpen() {
        return this != RECEIPT_DELIVERED && this != CANCELLED;
    }

    /**
     * Role-aware check. Providers may only move their own work forward in the field;
     * creating, assigning, cancelling and retrying are administrator actions.
     */
    public boolean canMoveTo(RequestStatus target, Role role, boolean isAssignedToActor) {
        if (target == null || !next().contains(target)) return false;
        if (role == Role.ADMIN) return true;
        if (role != Role.PROVIDER || !isAssignedToActor) return false;
        return (this == ASSIGNED && target == IN_PROGRESS)
                || (this == IN_PROGRESS && (target == PAYMENT_COMPLETED || target == FAILED))
                || (this == PAYMENT_COMPLETED && target == RECEIPT_DELIVERED);
    }
}
