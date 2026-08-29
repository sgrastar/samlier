package org.samlier.runner.outbox;

import java.net.URI;
import java.time.Clock;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import org.samlier.core.caseexec.ActionIds;
import org.samlier.core.caseexec.CaseExecution;
import org.samlier.core.caseexec.CaseExecutionRepository;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.caseexec.CaseState;
import org.samlier.core.caseexec.OutboundAction;
import org.samlier.core.caseexec.OutboundKind;

/** Non-evaluative ECP fixture. It creates a durable send intent without inventing a case verdict. */
public final class EcpProbeService {
    public static final String FIXTURE_ID = "fixture-ecp-http-basic";
    private static final String PHASE = "send-baseline";
    private final CaseExecutionRepository repository;
    private final InMemoryEphemeralCredentialProvider credentials;
    private final OutboundDispatcher dispatcher;
    private final Clock clock;

    public EcpProbeService(
            CaseExecutionRepository repository,
            InMemoryEphemeralCredentialProvider credentials,
            OutboundDispatcher dispatcher,
            Clock clock) {
        this.repository = Objects.requireNonNull(repository, "repository");
        this.credentials = Objects.requireNonNull(credentials, "credentials");
        this.dispatcher = Objects.requireNonNull(dispatcher, "dispatcher");
        this.clock = Objects.requireNonNull(clock, "clock");
    }

    public Result execute(String runId, URI endpoint, byte[] envelope, byte[] ephemeralCredential) {
        return execute(runId, FIXTURE_ID, endpoint, envelope, ephemeralCredential);
    }

    public Result execute(
            String runId, String fixtureId, URI endpoint, byte[] envelope, byte[] ephemeralCredential) {
        if (fixtureId == null || !fixtureId.matches("fixture-ecp-[a-z0-9-]+")) {
            throw new IllegalArgumentException("Invalid ECP fixture ID");
        }
        Objects.requireNonNull(endpoint, "endpoint");
        if (envelope == null || envelope.length == 0) throw new IllegalArgumentException("envelope must not be empty");
        if (ephemeralCredential == null || ephemeralCredential.length == 0) {
            throw new IllegalArgumentException("credential must not be empty");
        }
        var actionId = ActionIds.derive(runId, fixtureId, PHASE, 0);
        var action = new OutboundAction(actionId, OutboundKind.ECP_SOAP, envelope, endpoint, true);
        var existing = repository.find(runId, fixtureId);
        if (existing.isEmpty()) {
            var execution = new CaseExecution(
                    runId, fixtureId, 0, CaseExecutionStatus.RUNNING,
                    new CaseState(PHASE, Map.of("fixture", fixtureId)),
                    null, null, clock.instant());
            repository.apply(-1, execution, List.of(action));
        }
        var outbox = repository.findOutbox(actionId)
                .orElseThrow(() -> new IllegalStateException("ECP probe outbox intent was not persisted"));
        if (outbox.status() == org.samlier.core.caseexec.OutboxStatus.PENDING
                || outbox.status() == org.samlier.core.caseexec.OutboxStatus.BLOCKED_ON_CREDENTIAL) {
            credentials.put(runId, actionId, ephemeralCredential);
        }
        try {
            var result = dispatcher.dispatch(actionId);
            var stored = repository.findOutbox(actionId).orElseThrow();
            return new Result(actionId, result.state(), stored.status(), stored.transcriptEntryId(), result.incidents());
        } finally {
            credentials.discard(runId, actionId);
        }
    }

    public record Result(
            String actionId,
            OutboundDispatcher.State dispatchState,
            org.samlier.core.caseexec.OutboxStatus outboxStatus,
            String responseTranscriptId,
            List<org.samlier.core.evaluation.SuiteIncident> incidents) {
        public Result { incidents = List.copyOf(incidents); }
    }
}
