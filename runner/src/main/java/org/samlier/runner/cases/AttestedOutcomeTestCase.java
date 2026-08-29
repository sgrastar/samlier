package org.samlier.runner.cases;

import java.time.Duration;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.TargetRole;

/**
 * Common suspension/resumption logic for approved ATTESTED case designs.
 *
 * <p>The answer-to-outcome mapping is fixed by the server-side case definition. The caller submits
 * only an option value and cannot manufacture an Outcome or Verdict.
 */
public final class AttestedOutcomeTestCase implements TestCase {
    private static final String WAITING_PHASE = "await-attestation";
    private static final int MAX_NOTE_LENGTH = 4_000;

    private final String id;
    private final TargetRole role;
    private final String questionKey;
    private final Duration ttl;
    private final Map<String, AttestationOption> options;

    public AttestedOutcomeTestCase(
            String id,
            TargetRole role,
            String questionKey,
            Duration ttl,
            List<AttestationOption> options) {
        this.id = text(id, "id");
        this.role = Objects.requireNonNull(role, "role");
        this.questionKey = text(questionKey, "questionKey");
        this.ttl = Objects.requireNonNull(ttl, "ttl");
        if (ttl.isZero() || ttl.isNegative()) throw new IllegalArgumentException("ttl must be positive");
        if (options == null || options.isEmpty()) {
            throw new IllegalArgumentException("options must not be empty");
        }
        var indexed = new LinkedHashMap<String, AttestationOption>();
        for (var option : options) {
            Objects.requireNonNull(option, "option");
            if (indexed.putIfAbsent(option.value(), option) != null) {
                throw new IllegalArgumentException("Duplicate attestation option: " + option.value());
            }
        }
        this.options = Collections.unmodifiableMap(new LinkedHashMap<>(indexed));
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return role; }
    public String questionKey() { return questionKey; }
    public List<AttestationOption> options() { return List.copyOf(options.values()); }

    @Override
    public CaseStep start(CaseContext context) {
        Objects.requireNonNull(context, "context");
        if (!context.interaction().allowAttestation()) {
            return new CaseStep.Finish(notVerified(
                    "interaction_disallowed", "attestation.interaction-disallowed", Map.of()));
        }
        return new CaseStep.AwaitAttestation(
                new CaseState(WAITING_PHASE, Map.of("question_key", questionKey)),
                List.of(), questionKey, ttl);
    }

    @Override
    public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(event, "event");
        if (!WAITING_PHASE.equals(state.phase()) || !questionKey.equals(state.data().get("question_key"))) {
            throw new IllegalArgumentException("Attestation state does not belong to this question");
        }
        if (event instanceof CaseEvent.TimedOut timedOut) {
            return new CaseStep.Finish(notVerified(
                    "timeout", "attestation.timeout",
                    Map.of("waited_seconds", timedOut.waited().toSeconds())));
        }
        if (event instanceof CaseEvent.Aborted aborted) {
            return new CaseStep.Finish(notVerified(
                    "user_skipped", "attestation.aborted",
                    Map.of("abort_reason", aborted.reason())));
        }
        if (!(event instanceof CaseEvent.Attested attested)) {
            throw new IllegalArgumentException("Expected an attestation event");
        }
        var option = options.get(attested.value());
        if (option == null) {
            throw new IllegalArgumentException("Unknown attestation option: " + attested.value());
        }
        if (attested.note().length() > MAX_NOTE_LENGTH) {
            throw new IllegalArgumentException("Attestation note exceeds " + MAX_NOTE_LENGTH + " characters");
        }
        var details = new LinkedHashMap<String, Object>();
        details.put("attested", true);
        details.put("attestation_option", option.value());
        if (!attested.note().isBlank()) details.put("attestation_note", attested.note());
        var evidence = List.of(new EvidenceRef(
                "attestation", "attestation:" + context.runId() + ":" + id));
        return new CaseStep.Finish(new CaseOutcome(
                option.outcome(), option.notVerifiedReason(), option.reasonCode(), option.reasonCode(),
                evidence, details));
    }

    private CaseOutcome notVerified(String reason, String reasonCode, Map<String, Object> details) {
        return new CaseOutcome(
                Outcome.NOT_VERIFIED, reason, reasonCode, reasonCode, List.of(), details);
    }

    private static String text(String value, String name) {
        if (value == null || value.isBlank()) {
            throw new IllegalArgumentException(name + " must not be blank");
        }
        return value;
    }
}
