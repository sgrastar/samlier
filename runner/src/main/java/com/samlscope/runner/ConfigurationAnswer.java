package com.samlscope.runner;

import java.util.Locale;
import com.samlscope.core.caseexec.CaseEvent;

/** Fixed answers to the common configuration prompt. */
public enum ConfigurationAnswer {
    CONFIRMED,
    CAPABILITY_ABSENT,
    TARGET_CONFIG_UNAVAILABLE,
    CAPABILITY_UNDETERMINED;

    public static ConfigurationAnswer parse(String value) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException("Configuration answer must not be blank");
        }
        try {
            return valueOf(value.toUpperCase(Locale.ROOT));
        } catch (IllegalArgumentException unknown) {
            throw new IllegalArgumentException("Unknown configuration answer: " + value, unknown);
        }
    }

    public CaseEvent event(String note) {
        note = note == null ? "" : note;
        return switch (this) {
            case CONFIRMED -> {
                if (!note.isBlank()) {
                    throw new IllegalArgumentException("A confirmed configuration does not accept a note");
                }
                yield new CaseEvent.ConfigConfirmed();
            }
            case CAPABILITY_ABSENT -> new CaseEvent.ConfigUnavailable(
                    CaseEvent.ConfigurationIssue.CAPABILITY_ABSENT, note);
            case TARGET_CONFIG_UNAVAILABLE -> new CaseEvent.ConfigUnavailable(
                    CaseEvent.ConfigurationIssue.TARGET_CONFIG_UNAVAILABLE, note);
            case CAPABILITY_UNDETERMINED -> new CaseEvent.ConfigUnavailable(
                    CaseEvent.ConfigurationIssue.CAPABILITY_UNDETERMINED, note);
        };
    }
}
