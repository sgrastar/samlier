package org.samlier.runner;

import java.util.ArrayList;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseExecution;
import org.samlier.core.caseexec.CaseExecutionRepository;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.evaluation.EvidenceRef;

/** Routes one inbound SAML message to exactly one persisted AwaitInbound execution. */
public final class InboundCaseRouter {
    private final CaseExecutionRepository repository;
    private final TestCaseRegistry registry;
    private final CaseExecutionService executions;

    public InboundCaseRouter(
            CaseExecutionRepository repository,
            TestCaseRegistry registry,
            CaseExecutionService executions) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.registry = Objects.requireNonNull(registry, "registry");
        this.executions = Objects.requireNonNull(executions, "executions");
    }

    public Optional<CaseExecution> route(
            String runId,
            String matcherKey,
            Map<String, String> observed,
            byte[] decodedSaml,
            EvidenceRef evidence,
            CaseContext context) {
        text(runId, "runId");
        text(matcherKey, "matcherKey");
        observed = Map.copyOf(observed == null ? Map.of() : observed);
        if (decodedSaml == null || decodedSaml.length == 0) {
            throw new IllegalArgumentException("decodedSaml must not be empty");
        }
        Objects.requireNonNull(evidence, "evidence");
        Objects.requireNonNull(context, "context");
        if (!runId.equals(context.runId())) throw new IllegalArgumentException("CaseContext belongs to another Run");

        var matches = new ArrayList<CaseExecution>();
        for (var execution : repository.list(runId)) {
            if (execution.status() != CaseExecutionStatus.WAITING_INBOUND) continue;
            var matcher = execution.waitCondition().inboundMatcher();
            if (!matcher.matcherKey().equals(matcherKey)) continue;
            if (observed.entrySet().containsAll(matcher.criteria().entrySet())) matches.add(execution);
        }
        if (matches.isEmpty()) return Optional.empty();
        if (matches.size() > 1) {
            throw new IllegalStateException("Inbound SAML message ambiguously matches cases: "
                    + matches.stream().map(CaseExecution::caseId).sorted().toList());
        }
        var execution = matches.getFirst();
        var testCase = registry.require(execution.caseId());
        return Optional.of(executions.resume(
                runId, testCase, context, new CaseEvent.InboundMessage(decodedSaml, evidence)));
    }

    private static void text(String value, String name) {
        if (value == null || value.isBlank()) throw new IllegalArgumentException(name + " must not be blank");
    }
}
