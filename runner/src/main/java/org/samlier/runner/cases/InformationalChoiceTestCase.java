package org.samlier.runner.cases;

import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.CaseStep;
import org.samlier.core.caseexec.TestCase;
import org.samlier.core.evaluation.CaseOutcome;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.plan.TargetRole;

/** Records an approved MAY/OPTIONAL choice without asking the operator to grade the target. */
public final class InformationalChoiceTestCase implements TestCase {
    private final String id;
    private final TargetRole role;

    public InformationalChoiceTestCase(String id, TargetRole role) {
        if (id == null || id.isBlank()) throw new IllegalArgumentException("id is required");
        this.id = id;
        this.role = Objects.requireNonNull(role, "role");
    }

    @Override public String id() { return id; }
    @Override public TargetRole role() { return role; }

    @Override
    public CaseStep start(CaseContext context) {
        Objects.requireNonNull(context, "context");
        return new CaseStep.Finish(new CaseOutcome(
                Outcome.SATISFIED_WITH_NOTE, null,
                "optional_choice_not_required", "optional.choice-not-required",
                List.of(), Map.of("operator_verdict_requested", false, "observed", false)));
    }

    @Override
    public CaseStep resume(CaseContext context, CaseState state, CaseEvent event) {
        throw new IllegalStateException("Informational choice cases complete during start");
    }
}
