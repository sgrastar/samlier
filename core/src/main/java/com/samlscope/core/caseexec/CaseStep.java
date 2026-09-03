package com.samlscope.core.caseexec;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Objects;
import com.samlscope.core.evaluation.CaseOutcome;

public sealed interface CaseStep {
    record Continue(CaseState next, List<OutboundAction> actions) implements CaseStep {
        public Continue { next = required(next); actions = copyActions(actions); }
    }

    record AwaitBrowser(
            CaseState next, List<OutboundAction> actions, URI startUrl, Duration ttl) implements CaseStep {
        public AwaitBrowser {
            next = required(next); actions = copyActions(actions); Objects.requireNonNull(startUrl, "startUrl"); ttl = requireTtl(ttl);
            if (!startUrl.isAbsolute()) throw new IllegalArgumentException("startUrl must be absolute");
        }
    }

    record AwaitConfig(
            CaseState next, List<OutboundAction> actions, String instructionKey, Duration ttl) implements CaseStep {
        public AwaitConfig {
            next = required(next); actions = copyActions(actions); text(instructionKey, "instructionKey"); ttl = requireTtl(ttl);
        }
    }

    record AwaitAttestation(
            CaseState next, List<OutboundAction> actions, String questionKey, Duration ttl) implements CaseStep {
        public AwaitAttestation {
            next = required(next); actions = copyActions(actions); text(questionKey, "questionKey"); ttl = requireTtl(ttl);
        }
    }

    record AwaitInbound(
            CaseState next, List<OutboundAction> actions, InboundMatcher matcher, Duration ttl) implements CaseStep {
        public AwaitInbound {
            next = required(next); actions = copyActions(actions); Objects.requireNonNull(matcher, "matcher"); ttl = requireTtl(ttl);
        }
    }

    record Finish(CaseOutcome outcome) implements CaseStep {
        public Finish { Objects.requireNonNull(outcome, "outcome"); }
    }

    private static CaseState required(CaseState state) {
        return Objects.requireNonNull(state, "next");
    }

    private static List<OutboundAction> copyActions(List<OutboundAction> value) {
        return List.copyOf(value == null ? List.of() : value);
    }

    private static Duration requireTtl(Duration value) {
        Objects.requireNonNull(value, "ttl");
        if (value.isZero() || value.isNegative()) throw new IllegalArgumentException("ttl must be positive");
        return value;
    }

    private static void text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
