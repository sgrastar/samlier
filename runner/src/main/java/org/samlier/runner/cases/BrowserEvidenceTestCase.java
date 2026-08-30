package org.samlier.runner.cases;

import java.net.URI;
import java.time.Duration;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.plan.TargetRole;

/** Requires a real user-agent step without asking the operator to grade the target. */
public final class BrowserEvidenceTestCase implements TestCase, BrowserPrompt {
    private static final String WAITING_PHASE = "await-browser";
    private final AttestedOutcomeTestCase evidence;
    private final URI publicBase;
    private final String browserInstructionsEn;
    private final Duration browserTtl;

    public BrowserEvidenceTestCase(
            AttestedOutcomeTestCase evidence,
            URI publicBase,
            String browserInstructionsEn,
            Duration browserTtl) {
        this.evidence = Objects.requireNonNull(evidence, "evidence");
        this.publicBase = Objects.requireNonNull(publicBase, "publicBase");
        if (!publicBase.isAbsolute()) throw new IllegalArgumentException("publicBase must be absolute");
        if (browserInstructionsEn == null || browserInstructionsEn.isBlank()) {
            throw new IllegalArgumentException("browserInstructionsEn must not be blank");
        }
        this.browserInstructionsEn = browserInstructionsEn;
        this.browserTtl = Objects.requireNonNull(browserTtl, "browserTtl");
        if (browserTtl.isZero() || browserTtl.isNegative()) {
            throw new IllegalArgumentException("browserTtl must be positive");
        }
    }

    @Override public String id() { return evidence.id(); }
    @Override public TargetRole role() { return evidence.role(); }
    @Override public String browserInstructionsEn() { return browserInstructionsEn; }

    @Override
    public CaseStep start(CaseContext context) {
        Objects.requireNonNull(context, "context");
        var start = publicBase.resolve("/browser/" + context.runId() + "/" + id());
        return new CaseStep.AwaitBrowser(
                new CaseState(WAITING_PHASE, Map.of("case_id", id())), List.of(), start, browserTtl);
    }

    @Override
    public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        Objects.requireNonNull(context, "context");
        Objects.requireNonNull(state, "state");
        Objects.requireNonNull(event, "event");
        if (!WAITING_PHASE.equals(state.phase())) {
            throw new IllegalArgumentException("Unexpected browser evidence phase");
        }
        if (!id().equals(state.data().get("case_id"))) {
            throw new IllegalArgumentException("Browser state belongs to another case");
        }
        if (event instanceof CaseEvent.BrowserReturned) {
            return new CaseStep.Finish(new org.samlier.core.evaluation.CaseOutcome(
                    org.samlier.core.evaluation.Outcome.NOT_VERIFIED,
                    "automatic_oracle_unavailable", "browser.oracle-unavailable",
                    "browser.oracle-unavailable", List.of(),
                    Map.of("browser_action_completed", true,
                            "operator_verdict_requested", false)));
        }
        if (event instanceof CaseEvent.TimedOut timedOut) {
            return new CaseStep.Finish(new org.samlier.core.evaluation.CaseOutcome(
                    org.samlier.core.evaluation.Outcome.NOT_VERIFIED,
                    "timeout", "browser.timeout", "browser.timeout", List.of(),
                    Map.of("waited_seconds", timedOut.waited().toSeconds())));
        }
        if (event instanceof CaseEvent.Aborted) {
            return new CaseStep.Finish(org.samlier.core.evaluation.CaseOutcome.notVerified(
                    "user_skipped", "browser.aborted"));
        }
        throw new IllegalArgumentException("Expected a browser completion event");
    }
}
