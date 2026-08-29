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

    record ConfigConfirmed() implements CaseEvent {}

    record Attested(String value, String note) implements CaseEvent {
        public Attested { text(value, "value"); note = note == null ? "" : note; }
    }

    record TimedOut(Duration waited) implements CaseEvent {
        public TimedOut {
            if (waited == null || waited.isNegative()) throw new IllegalArgumentException("waited is invalid");
        }
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

    private static void text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
