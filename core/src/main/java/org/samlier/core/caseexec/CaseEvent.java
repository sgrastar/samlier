package org.samlier.core.caseexec;

import java.time.Duration;
import java.util.Map;
import org.samlier.core.evaluation.EvidenceRef;

public sealed interface CaseEvent {
    record InboundMessage(byte[] decodedSaml, EvidenceRef evidence) implements CaseEvent {
        public InboundMessage {
            if (decodedSaml == null || decodedSaml.length == 0) {
                throw new IllegalArgumentException("decodedSaml must not be empty");
            }
            decodedSaml = decodedSaml.clone();
            if (evidence == null) throw new IllegalArgumentException("evidence is required");
        }

        @Override public byte[] decodedSaml() { return decodedSaml.clone(); }
    }

    record BrowserReturned(String acsPath) implements CaseEvent {
        public BrowserReturned { text(acsPath, "acsPath"); }
    }

    /** Internal signal that the Suite, not the operator, observed sufficient Transcript evidence. */
    record TranscriptReady() implements CaseEvent {}

    record ConfigConfirmed() implements CaseEvent {}

    record ConfigUnavailable(ConfigurationIssue issue, String note) implements CaseEvent {
        public ConfigUnavailable {
            if (issue == null) throw new IllegalArgumentException("issue is required");
            note = note == null ? "" : note;
        }
    }

    record Attested(String value, String note) implements CaseEvent {
        public Attested { text(value, "value"); note = note == null ? "" : note; }
    }

    record TimedOut(Duration waited) implements CaseEvent {
        public TimedOut {
            if (waited == null || waited.isNegative()) throw new IllegalArgumentException("waited is invalid");
        }
    }

    /** The browser returned no protocol message for the current inbound fixture. */
    record InboundUnavailable(String reason) implements CaseEvent {
        public InboundUnavailable { text(reason, "reason"); }
    }

    record Aborted(String reason) implements CaseEvent {
        public Aborted { text(reason, "reason"); }
    }

    record Custom(String type, Map<String, Object> data) implements CaseEvent {
        public Custom {
            text(type, "type");
            data = new CaseState("event", data).data();
        }
    }

    enum ConfigurationIssue {
        CAPABILITY_ABSENT,
        TARGET_CONFIG_UNAVAILABLE,
        CAPABILITY_UNDETERMINED
    }

    private static void text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
