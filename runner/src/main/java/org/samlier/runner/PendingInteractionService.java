package org.samlier.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;
import org.samlier.core.caseexec.CaseExecutionRepository;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.runner.cases.AttestedOutcomeTestCase;

/** Projects persisted user waits without exposing restart state or any client-writable outcome. */
public final class PendingInteractionService implements InteractionQuery {
    private static final List<String> CONFIGURATION_ANSWERS = List.of(
            "confirmed", "capability_absent", "target_config_unavailable", "capability_undetermined");

    private final CaseExecutionRepository executions;
    private final TestCaseRegistry registry;

    public PendingInteractionService(CaseExecutionRepository executions, TestCaseRegistry registry) {
        this.executions = Objects.requireNonNull(executions, "executions");
        this.registry = Objects.requireNonNull(registry, "registry");
    }

    @Override
    public List<PendingInteraction> pending(String runId) {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId must not be blank");
        var result = new ArrayList<PendingInteraction>();
        for (var execution : executions.list(runId)) {
            var testCase = registry.require(execution.caseId());
            var wait = execution.waitCondition();
            if (execution.status() == CaseExecutionStatus.WAITING_BROWSER) {
                result.add(new PendingInteraction(
                        execution.caseId(), Kind.BROWSER, null, wait.startUrl(), wait.expiresAt(), List.of()));
            } else if (execution.status() == CaseExecutionStatus.WAITING_CONFIG) {
                result.add(new PendingInteraction(
                        execution.caseId(), Kind.CONFIGURATION, wait.promptKey(), null, wait.expiresAt(),
                        CONFIGURATION_ANSWERS));
            } else if (execution.status() == CaseExecutionStatus.WAITING_ATTESTATION) {
                if (!(testCase instanceof AttestedOutcomeTestCase attested)) {
                    throw new IllegalStateException(
                            "Attestation case does not expose server-defined options: " + execution.caseId());
                }
                result.add(new PendingInteraction(
                        execution.caseId(), Kind.ATTESTATION, wait.promptKey(), null, wait.expiresAt(),
                        attested.options().stream().map(option -> option.value()).toList()));
            }
        }
        return List.copyOf(result);
    }
}
