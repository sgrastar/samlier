package org.samlier.runner.cases;

import java.time.Duration;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.ConfigurationFailureSemantics;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.TargetRole;

/** Adds the common approved configuration branch in front of a concrete observation case. */
public final class ConfigurationGateTestCase implements TestCase, AttestationPrompt, ConfigurationPrompt {
    private static final String WAITING_PHASE = "await-configuration";
    private static final int MAX_NOTE_LENGTH = 4_000;

    private final TestCase delegate;
    private final String instructionKey;
    private final String instructionEn;
    private final Duration ttl;
    private final ConfigurationFailureSemantics semantics;

    public ConfigurationGateTestCase(
            TestCase delegate,
            String instructionKey,
            Duration ttl,
            ConfigurationFailureSemantics semantics) {
        this(delegate, instructionKey, instructionKey, ttl, semantics);
    }

    public ConfigurationGateTestCase(
            TestCase delegate,
            String instructionKey,
            String instructionEn,
            Duration ttl,
            ConfigurationFailureSemantics semantics) {
        this.delegate = Objects.requireNonNull(delegate, "delegate");
        this.instructionKey = text(instructionKey, "instructionKey");
        this.instructionEn = text(instructionEn, "instructionEn");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("ttl must be positive");
        this.semantics = Objects.requireNonNull(semantics, "semantics");
    }

    @Override public String id() { return delegate.id(); }
    @Override public TargetRole role() { return delegate.role(); }
    public String instructionKey() { return instructionKey; }
    @Override public String instructionEn() { return instructionEn; }
    public ConfigurationFailureSemantics semantics() { return semantics; }
    @Override public String promptEn() {
        if (delegate instanceof AttestationPrompt prompt) return prompt.promptEn();
        throw new IllegalStateException("Configuration delegate has no attestation prompt: " + id());
    }
    @Override public List<AttestationOption> options() {
        if (delegate instanceof AttestationPrompt prompt) return prompt.options();
        throw new IllegalStateException("Configuration delegate has no attestation options: " + id());
    }

    @Override
    public CaseStep start(CaseContext context) {
        Objects.requireNonNull(context, "context");
        return new CaseStep.AwaitConfig(
                new CaseState(WAITING_PHASE, Map.of("instruction_key", instructionKey)),
                List.of(), instructionKey, ttl);
    }

    @Override
    public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(event, "event");
        if (!WAITING_PHASE.equals(state.phase())) {
            return delegate.resume(context, state, event);
        }
        if (!instructionKey.equals(state.data().get("instruction_key"))) {
            throw new IllegalArgumentException("Configuration state does not belong to this instruction");
        }
        if (event instanceof CaseEvent.ConfigConfirmed) return delegate.start(context);
        if (event instanceof CaseEvent.ConfigUnavailable unavailable) return unavailable(unavailable);
        if (event instanceof CaseEvent.TimedOut timedOut) {
            return finishNotVerified(
                    "timeout", "configuration.timeout",
                    Map.of("waited_seconds", timedOut.waited().toSeconds()));
        }
        if (event instanceof CaseEvent.Aborted aborted) {
            return finishNotVerified(
                    "user_skipped", "configuration.aborted",
                    Map.of("abort_reason", aborted.reason()));
        }
        throw new IllegalArgumentException("Expected a configuration event");
    }

    private CaseStep unavailable(CaseEvent.ConfigUnavailable event) {
        if (event.note().length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("Configuration note exceeds " + MAX_NOTE_LENGTH + " characters");
        }
        var details = new LinkedHashMap<String, Object>();
        details.put("configuration_issue", event.issue().name().toLowerCase(java.util.Locale.ROOT));
        if (!event.note().isBlank()) details.put("configuration_note", event.note());
        return switch (event.issue()) {
            case CAPABILITY_ABSENT -> semantics == ConfigurationFailureSemantics.NORMATIVE_CAPABILITY
                    ? new CaseStep.Finish(new CaseOutcome(
                            Outcome.VIOLATED, null, "capability_absent", "configuration.capability-absent",
                            List.of(), details))
                    : finishNotVerified(
                            "test_precondition_unavailable", "configuration.test-precondition-unavailable", details);
            case TARGET_CONFIG_UNAVAILABLE -> finishNotVerified(
                    "target_config_unavailable", "configuration.target-unavailable", details);
            case CAPABILITY_UNDETERMINED -> finishNotVerified(
                    "capability_undetermined", "configuration.capability-undetermined", details);
        };
    }

    private CaseStep finishNotVerified(String reason, String reasonCode, Map<String, Object> details) {
        return new CaseStep.Finish(new CaseOutcome(
                Outcome.NOT_VERIFIED, reason, reasonCode, reasonCode, List.of(), details));
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
