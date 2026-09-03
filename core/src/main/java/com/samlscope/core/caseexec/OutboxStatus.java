package com.samlscope.core.caseexec;

public enum OutboxStatus {
    PENDING,
    SENDING,
    UNKNOWN_DELIVERY,
    BLOCKED_ON_CREDENTIAL,
    SENT;

    public boolean canTransitionTo(OutboxStatus next) {
        return switch (this) {
            case PENDING -> next == SENDING || next == BLOCKED_ON_CREDENTIAL;
            case BLOCKED_ON_CREDENTIAL -> next == SENDING || next == BLOCKED_ON_CREDENTIAL;
            case SENDING -> next == SENT || next == UNKNOWN_DELIVERY;
            case UNKNOWN_DELIVERY -> next == SENDING || next == SENT;
            case SENT -> false;
        };
    }
}
