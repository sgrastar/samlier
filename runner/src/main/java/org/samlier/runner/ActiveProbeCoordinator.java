package org.samlier.runner;

import java.net.URI;
import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Duration;
import java.util.Base64;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Optional;
import java.util.function.BiFunction;
import org.samlier.core.caseexec.CaseEvent;
import org.samlier.core.caseexec.CaseExecution;
import org.samlier.core.caseexec.CaseExecutionRepository;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.caseexec.OutboxStatus;
import org.samlier.core.evaluation.EvidenceRef;
import org.samlier.core.plan.PlanRepository;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.RunRepository;
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptInput;
import org.samlier.core.transcript.TranscriptRecorder;
import org.samlier.runner.cases.IdpErrorProbeConfiguration;
import org.samlier.runner.cases.IdpErrorResponseTestCase;
import org.samlier.runner.outbox.OutboundDispatcher;

/** Bridges persisted active-probe cases to a real SAML browser front channel. */
public final class ActiveProbeCoordinator {
    private final URI publicBase;
    private final PlanRepository plans;
    private final RunRepository runs;
    private final CaseExecutionRepository repository;
    private final OutboundDispatcher dispatcher;
    private final TranscriptRecorder transcript;
    private final CaseContextProvider contexts;
    private final BiFunction<TestPlan, String, IdpErrorProbeConfiguration> configurations;
    private final Clock clock;

    public ActiveProbeCoordinator(
            URI publicBase,
            PlanRepository plans,
            RunRepository runs,
            CaseExecutionRepository repository,
            OutboundDispatcher dispatcher,
            TranscriptRecorder transcript,
            CaseContextProvider contexts,
            BiFunction<TestPlan, String, IdpErrorProbeConfiguration> configurations,
            Clock clock) {
        this.publicBase = Objects.requireNonNull(publicBase, "publicBase");
        this.plans = Objects.requireNonNull(plans, "plans");
        this.runs = Objects.requireNonNull(runs, "runs");
        this.repository = Objects.requireNonNull(repository, "repository");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.transcript = Objects.requireNonNull(transcript, "transcript");
        this.contexts = Objects.requireNonNull(contexts, "contexts");
        this.configurations = Objects.requireNonNull(configurations, "configurations");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Status status(String runId) {
        var run = requireRun(runId);
        var execution = repository.find(runId, IdpErrorResponseTestCase.CASE_ID);
        if (execution.isEmpty()) return new Status(run.planId(), State.NOT_STARTED, null, null, false, null);
        var current = execution.orElseThrow();
        if (current.status() == CaseExecutionStatus.FINISHED) {
            return new Status(run.planId(), State.FINISHED, null, null, false,
                    current.outcome() == null ? null : current.outcome().outcome().name());
        }
        if (current.status() != CaseExecutionStatus.WAITING_INBOUND) {
            return new Status(run.planId(), State.UNAVAILABLE, null, null, false, null);
        }
        var action = repository.listOutbox(runId).stream()
                .filter(value -> value.caseId().equals(IdpErrorResponseTestCase.CASE_ID))
                .filter(value -> value.action().actionId().equals(
                        current.waitCondition().inboundMatcher().criteria().get("ScenarioActionId")))
                .findFirst().orElseThrow(() -> new IllegalStateException("Active probe has no matching outbox action"));
        var state = action.status() == OutboxStatus.PENDING ? State.READY : State.AWAITING_RESPONSE;
        var startUrl = state == State.READY
                ? publicBase.resolve("/p/" + run.planId() + "/probe/" + action.action().actionId()
                        + "?run=" + url(runId))
                : null;
        return new Status(run.planId(), state, action.action().actionId(), startUrl,
                IdpErrorResponseTestCase.PASSIVE_FIXTURE_ID.equals(
                        current.state().data().get("fixture_id")), null);
    }

    /** Expires this coordinator's Plan-specific case without exposing it to a static registry. */
    public Optional<CaseExecution> expireReady(String runId) {
        var run = requireRun(runId);
        var current = repository.find(runId, IdpErrorResponseTestCase.CASE_ID);
        if (current.isEmpty() || current.orElseThrow().status() == CaseExecutionStatus.FINISHED) {
            return Optional.empty();
        }
        var execution = current.orElseThrow();
        var wait = execution.waitCondition();
        var now = clock.instant();
        if (wait == null || now.isBefore(wait.expiresAt())) return Optional.empty();
        var plan = plans.find(run.planId()).orElseThrow(() -> new IllegalStateException("Run has no Test Plan"));
        var waited = Duration.between(execution.updatedAt(), now);
        if (waited.isNegative()) waited = Duration.ZERO;
        return Optional.of(new CaseExecutionService(repository).resume(
                runId, new IdpErrorResponseTestCase(configurations.apply(plan, runId)),
                contexts.contextFor(runId), new CaseEvent.TimedOut(waited)));
    }

    public PreparedProbe prepare(String runId, String actionId, boolean freshSessionConfirmed) {
        var status = status(runId);
        if (status.state() != State.READY || !Objects.equals(status.actionId(), actionId)) {
            throw new IllegalStateException("Active probe action is not ready");
        }
        if (status.requiresFreshSession() && !freshSessionConfirmed) {
            throw new IllegalArgumentException("The passive probe requires a browser context with no target session");
        }
        var relayState = ActiveProbeCorrelation.encode(runId, actionId);
        var dispatch = dispatcher.dispatchFrontChannel(actionId, action -> {
            var encodedRequest = Base64.getEncoder().encodeToString(action.payload());
            var body = "SAMLRequest=" + url(encodedRequest) + "&RelayState=" + url(relayState);
            return transcript.record(new TranscriptInput(
                    runId, Direction.OUTBOUND, clock.instant(), action.actionId(), "POST",
                    action.target().toString(), null,
                    Map.of("Content-Type", List.of("application/x-www-form-urlencoded")),
                    body.getBytes(StandardCharsets.UTF_8), "application/x-www-form-urlencoded",
                    null, action.payload(),
                    Map.of("type", "AuthnRequest", "active_probe", true,
                            "action_id", action.actionId()))).id();
        });
        return new PreparedProbe(
                dispatch.action().target(),
                Base64.getEncoder().encodeToString(dispatch.action().payload()),
                relayState,
                dispatch.transcriptEntryId());
    }

    public Status accept(
            String runId,
            String actionId,
            byte[] decodedSaml,
            EvidenceRef evidence) {
        requireRun(runId);
        var outbox = repository.findOutbox(actionId)
                .orElseThrow(() -> new IllegalArgumentException("Unknown active-probe action"));
        if (!outbox.runId().equals(runId)
                || !outbox.caseId().equals(IdpErrorResponseTestCase.CASE_ID)) {
            throw new IllegalArgumentException("Active-probe correlation belongs to another execution");
        }
        if (outbox.status() == OutboxStatus.UNKNOWN_DELIVERY) {
            dispatcher.confirmInboundDelivery(actionId, evidence.reference());
        } else if (outbox.status() != OutboxStatus.SENT) {
            throw new IllegalStateException("Inbound response arrived before front-channel dispatch");
        }
        var current = repository.find(runId, IdpErrorResponseTestCase.CASE_ID)
                .orElseThrow(() -> new IllegalStateException("Active-probe execution is missing"));
        if (current.status() == CaseExecutionStatus.FINISHED) {
            // A response may cross the timeout boundary after the raw message has already been
            // durably recorded. Preserve the Suite-side NOT_VERIFIED result and acknowledge the
            // late delivery instead of turning the browser POST into an application error.
            return status(runId);
        }
        var waitingAction = current.waitCondition() == null
                ? null : current.waitCondition().inboundMatcher().criteria().get("ScenarioActionId");
        if (!actionId.equals(waitingAction)) {
            // Browser retries of an already-consumed POST must be idempotent. The prior fixture is
            // already represented by Transcript evidence and must not advance the next fixture.
            return status(runId);
        }
        var run = requireRun(runId);
        var plan = plans.find(run.planId()).orElseThrow(() -> new IllegalStateException("Run has no Test Plan"));
        var testCase = new IdpErrorResponseTestCase(configurations.apply(plan, runId));
        var router = new InboundCaseRouter(
                repository, new TestCaseRegistry(List.of(testCase)), new CaseExecutionService(repository));
        router.route(
                runId, "saml-response", Map.of("ScenarioActionId", actionId), decodedSaml,
                evidence, contexts.contextFor(runId))
                .orElseThrow(() -> new IllegalStateException("Active-probe response did not match the waiting case"));
        return status(runId);
    }

    private org.samlier.core.run.TestRun requireRun(String runId) {
        if (runId == null || runId.isBlank()) throw new IllegalArgumentException("runId must not be blank");
        return runs.find(runId).orElseThrow(() -> new IllegalArgumentException("Unknown Run"));
    }

    private static String url(String value) {
        return URLEncoder.encode(value, StandardCharsets.UTF_8);
    }

    public record Status(
            String planId,
            State state,
            String actionId,
            URI startUrl,
            boolean requiresFreshSession,
            String outcome) {
        public Status {
            if (planId == null || planId.isBlank()) throw new IllegalArgumentException("planId is required");
        }
    }

    public record PreparedProbe(
            URI destination,
            String samlRequest,
            String relayState,
            String transcriptEntryId) {
        public PreparedProbe {
            Objects.requireNonNull(destination, "destination");
            if (samlRequest == null || samlRequest.isBlank()) throw new IllegalArgumentException("samlRequest is required");
            if (relayState == null || relayState.isBlank()) throw new IllegalArgumentException("relayState is required");
            if (transcriptEntryId == null || transcriptEntryId.isBlank()) {
                throw new IllegalArgumentException("transcriptEntryId is required");
            }
        }
    }

    public enum State { NOT_STARTED, READY, AWAITING_RESPONSE, FINISHED, UNAVAILABLE }
}
