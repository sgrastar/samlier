package org.samlier.runner.cases;

import static org.junit.jupiter.api.Assertions.assertEquals;

import java.nio.charset.StandardCharsets;
import java.time.Clock;
import java.time.Instant;
import java.time.ZoneOffset;
import java.util.List;
import java.util.Map;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.io.TempDir;
import org.samlier.core.evaluation.Outcome;
import org.samlier.core.caseexec.CaseContext;
import org.samlier.core.caseexec.CaseExecutionStatus;
import org.samlier.core.plan.MetadataDeliveryKind;
import org.samlier.core.plan.MetadataSourceKind;
import org.samlier.core.plan.PlanProfile;
import org.samlier.core.plan.TargetKind;
import org.samlier.core.plan.TargetRole;
import org.samlier.core.plan.TestPlan;
import org.samlier.core.run.Reachability;
import org.samlier.core.run.RunStatus;
import org.samlier.core.run.TestRun;
import org.samlier.core.transcript.Direction;
import org.samlier.core.transcript.TranscriptInput;
import org.samlier.store.FileTranscriptRecorder;
import org.samlier.store.JsonCodec;
import org.samlier.store.SqliteDatabase;
import org.samlier.store.SqlitePlanRepository;
import org.samlier.store.SqliteRunRepository;
import org.samlier.store.SqliteCaseExecutionRepository;
import org.samlier.runner.CaseExecutionService;

class DtdFreeTranscriptCaseTest {
    @TempDir java.nio.file.Path directory;

    @Test
    void inspectsPersistedInboundTargetMessagesAndIgnoresSuiteOutboundMessages() {
        var database = new SqliteDatabase(directory);
        var json = new JsonCodec();
        var now = Instant.parse("2026-08-29T00:00:00Z");
        var plan = new TestPlan(
                "plan_0123456789ABCDEFGHJKMNPQRS", "DTD Transcript", PlanProfile.IDP_CORE,
                new TestPlan.Target(TargetKind.IDP, "https://idp.example/entity",
                        new TestPlan.MetadataSource(MetadataSourceKind.URL, "https://idp.example/metadata")),
                MetadataDeliveryKind.MANUAL, Map.of(), TestPlan.Parameters.defaults(),
                TestPlan.Interaction.defaults(), now, now);
        new SqlitePlanRepository(database, json).save(plan);
        var run = new TestRun(
                "run_0123456789ABCDEFGHJKMNPQRS", plan.id(), RunStatus.RUNNING,
                Reachability.UNKNOWN, Map.of(), now, now);
        new SqliteRunRepository(database, json).save(run);
        var transcript = new FileTranscriptRecorder(database, json, directory);
        record(transcript, run.id(), Direction.OUTBOUND,
                "<!DOCTYPE suite [<!ELEMENT suite EMPTY>]><suite/>", now);
        record(transcript, run.id(), Direction.INBOUND, "<Response/>", now.plusSeconds(1));
        var violating = record(transcript, run.id(), Direction.INBOUND,
                "<!DOCTYPE Response [<!ELEMENT Response EMPTY>]><Response/>", now.plusSeconds(2));

        var outcome = new DtdFreeTargetSamlCase().evaluateTranscript(run.id(), transcript, transcript);

        assertEquals(Outcome.VIOLATED, outcome.outcome());
        assertEquals(List.of("transcript:" + violating.id()),
                outcome.evidence().stream().map(value -> value.reference()).toList());
        assertEquals(2, outcome.details().get("inspected_messages"));

        var service = new CaseExecutionService(new SqliteCaseExecutionRepository(database, json));
        var execution = service.start(run.id(),
                new DtdFreeTranscriptTestCase("IIP-G03-a-idp-01", transcript),
                new CaseContext() {
                    @Override public String runId() { return run.id(); }
                    @Override public TargetRole targetRole() { return TargetRole.IDP; }
                    @Override public Clock clock() { return Clock.fixed(now, ZoneOffset.UTC); }
                    @Override public TestPlan.Parameters parameters() { return plan.parameters(); }
                    @Override public TestPlan.Interaction interaction() { return plan.interaction(); }
                    @Override public Reachability reachability() { return run.targetToSuiteReachability(); }
                    @Override public org.samlier.core.transcript.TranscriptRecorder transcript() { return transcript; }
                    @Override public boolean transcriptComplete() { return true; }
                });
        assertEquals(CaseExecutionStatus.FINISHED, execution.status());
        assertEquals(Outcome.VIOLATED, execution.outcome().outcome());
    }

    private org.samlier.core.transcript.TranscriptEntry record(
            FileTranscriptRecorder recorder,
            String runId,
            Direction direction,
            String xml,
            Instant timestamp) {
        var bytes = xml.getBytes(StandardCharsets.UTF_8);
        return recorder.record(new TranscriptInput(
                runId, direction, timestamp, "corr", "POST", "https://suite.example/peer", 200,
                Map.of(), bytes, "application/xml", null, bytes, Map.of()));
    }
}
